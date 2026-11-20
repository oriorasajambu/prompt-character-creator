package com.promptgenerator

import com.github.ajalt.clikt.core.Abort
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import java.nio.file.Path

class GenerateCommand : CliktCommand(name = "prompt-generator") {
    private val charactersPath by option("--characters", help = "Character list file (default: characters.txt).")
        .default("characters.txt")
    private val templatesPath by option("--templates", help = "Templates JSON file (default: templates.json).")
        .default("templates.json")
    private val outputDirectory by option("--output-dir", help = "Output directory (default: generated).")
        .default("generated")
    private val selectedTemplates by option("--template", help = "Template name to generate; repeat to select multiple.")
        .multiple()
    private val overwrite by option("--overwrite", help = "Replace output files that already exist.").flag()
    private val interactive by option("--interactive", help = "Prompt for paths and confirm the output directory.")
        .flag()
    private val dryRun by option("--dry-run", help = "Validate and list outputs without writing files.").flag()
    private val verbose by option("--verbose", help = "Print stack traces for errors.").flag()

    override fun help(context: Context) = "Generate character-specific prompt files."

    override fun run() {
        try {
            val paths = if (interactive) promptForPaths() else Triple(charactersPath, templatesPath, outputDirectory)
            val options =
                GeneratorOptions(
                    charactersPath = Path.of(paths.first),
                    templatesPath = Path.of(paths.second),
                    outputDirectory = Path.of(paths.third),
                    selectedTemplates = selectedTemplates,
                    overwrite = overwrite,
                    dryRun = dryRun,
                )
            val plan = PromptGenerator().generate(options)

            if (dryRun) {
                plan.outputs.forEach { echo("${it.path}: ${it.prompts.size} prompt(s)") }
                echo("Dry run: ${plan.totalPrompts} prompt(s) across ${plan.outputs.size} file(s).")
            } else {
                echo("Generated ${plan.totalPrompts} prompt(s) across ${plan.outputs.size} file(s) in '${paths.third}'.")
            }
        } catch (exception: PromptGeneratorException) {
            echo("Error: ${exception.message}", err = true)
            if (verbose) exception.printStackTrace(System.err)
            throw Abort()
        } catch (exception: Exception) {
            echo("Error: ${exception.message ?: exception::class.simpleName}", err = true)
            if (verbose) exception.printStackTrace(System.err)
            throw Abort()
        }
    }

    private fun promptForPaths(): Triple<String, String, String> {
        val characters = readPrompt("Characters file", charactersPath)
        val templates = readPrompt("Templates JSON file", templatesPath)
        val output = readPrompt("Output directory", outputDirectory)
        val confirmed = readPrompt("Generate into '$output'? [y/N]", "n")
        if (!confirmed.equals("y", ignoreCase = true)) {
            throw PromptGeneratorException("Generation cancelled.")
        }
        return Triple(characters, templates, output)
    }

    private fun readPrompt(
        label: String,
        default: String,
    ): String {
        print("$label [$default]: ")
        System.out.flush()
        val value = readLine() ?: throw PromptGeneratorException("Interactive input ended unexpectedly.")
        return value.trim().ifEmpty { default }
    }
}

fun main(args: Array<String>) = GenerateCommand().main(args)
