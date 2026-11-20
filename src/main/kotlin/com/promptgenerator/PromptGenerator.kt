package com.promptgenerator

import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.Locale

data class GeneratorOptions(
    val charactersPath: Path,
    val templatesPath: Path,
    val outputDirectory: Path,
    val selectedTemplates: List<String> = emptyList(),
    val overwrite: Boolean = false,
    val dryRun: Boolean = false,
)

data class GenerationPlan(
    val characterCount: Int,
    val outputs: List<PlannedOutput>,
) {
    val totalPrompts: Long
        get() = characterCount.toLong() * outputs.size
}

data class PlannedOutput(
    val templateName: String,
    val path: Path,
    val prompts: List<String>,
)

class PromptGeneratorException(message: String, cause: Throwable? = null) : Exception(message, cause)

@Serializable
private data class TemplateCatalog(
    val templates: Map<String, TemplateDefinition>,
)

@Serializable
private data class TemplateDefinition(
    val prompt: String,
)

class PromptGenerator {
    private val json = Json { ignoreUnknownKeys = true }

    fun generate(options: GeneratorOptions): GenerationPlan {
        val characters = readCharacters(options.charactersPath)
        val templates = readTemplates(options.templatesPath)
        val selected = selectTemplates(templates, options.selectedTemplates)

        if (selected.isEmpty()) {
            throw PromptGeneratorException("No templates were selected.")
        }

        val plannedOutputs = selected.map { (name, template) ->
            validateTemplate(name, template)
            PlannedOutput(
                templateName = name,
                path = options.outputDirectory.resolve("${sanitizeFileName(name)}.txt"),
                prompts = characters.map { template.replace(CHARACTER_TOKEN, it) },
            )
        }

        checkOutputDirectory(options.outputDirectory)
        checkOutputCollisions(plannedOutputs)
        checkExistingOutputs(plannedOutputs, options.overwrite)

        val plan = GenerationPlan(characters.size, plannedOutputs)
        if (!options.dryRun) {
            writeOutputs(plan, options.outputDirectory, options.overwrite)
        }
        return plan
    }

    private fun readCharacters(path: Path): List<String> {
        val characters = readUtf8(path).lineSequence()
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .toList()

        if (characters.isEmpty()) {
            throw PromptGeneratorException("No characters found in '${path}'.")
        }

        val duplicates = characters.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        duplicates.forEach { System.err.println("Warning: duplicate character '$it' will produce duplicate prompts.") }
        return characters
    }

    private fun readTemplates(path: Path): Map<String, String> {
        val text = readUtf8(path)
        return try {
            json.decodeFromString<TemplateCatalog>(text).templates.mapValues { it.value.prompt }
        } catch (exception: SerializationException) {
            throw PromptGeneratorException("Invalid templates JSON in '${path}': ${exception.message}", exception)
        }
    }

    private fun selectTemplates(
        templates: Map<String, String>,
        selectedNames: List<String>,
    ): List<Pair<String, String>> {
        if (templates.isEmpty()) {
            throw PromptGeneratorException("No templates are defined.")
        }
        if (selectedNames.isEmpty()) return templates.entries.map { it.key to it.value }

        val unknownNames = selectedNames.distinct().filterNot(templates::containsKey)
        if (unknownNames.isNotEmpty()) {
            throw PromptGeneratorException("Unknown template(s): ${unknownNames.joinToString()}.")
        }
        return selectedNames.distinct().map { it to templates.getValue(it) }
    }

    private fun validateTemplate(name: String, template: String) {
        if (name.isBlank()) {
            throw PromptGeneratorException("Template names must not be blank.")
        }
        if (template.isBlank()) {
            throw PromptGeneratorException("Template '$name' must not be blank.")
        }
        if ('\n' in template || '\r' in template) {
            throw PromptGeneratorException("Template '$name' must be a single line.")
        }
        if (template.windowed(CHARACTER_TOKEN.length, 1).count { it == CHARACTER_TOKEN } != 1) {
            throw PromptGeneratorException("Template '$name' must contain exactly one $CHARACTER_TOKEN token.")
        }
    }

    private fun checkOutputDirectory(directory: Path) {
        if (Files.exists(directory) && !Files.isDirectory(directory)) {
            throw PromptGeneratorException("Output path '$directory' exists and is not a directory.")
        }
    }

    private fun checkOutputCollisions(outputs: List<PlannedOutput>) {
        val collision = outputs.groupBy { it.path.fileName.toString().lowercase(Locale.ROOT) }
            .entries.firstOrNull { it.value.size > 1 }
        if (collision != null) {
            val names = collision.value.joinToString { it.templateName }
            throw PromptGeneratorException("Template names produce a case-insensitive filename collision: $names.")
        }
    }

    private fun checkExistingOutputs(outputs: List<PlannedOutput>, overwrite: Boolean) {
        val invalidDestination = outputs.firstOrNull {
            Files.exists(it.path, LinkOption.NOFOLLOW_LINKS) &&
                !Files.isRegularFile(it.path, LinkOption.NOFOLLOW_LINKS)
        }
        if (invalidDestination != null) {
            throw PromptGeneratorException("Output destination '${invalidDestination.path}' is not a regular file.")
        }
        if (overwrite) return
        val existing = outputs.filter { Files.exists(it.path) }
        if (existing.isNotEmpty()) {
            throw PromptGeneratorException(
                "Output file(s) already exist (use --overwrite to replace them): " +
                    existing.joinToString { it.path.toString() },
            )
        }
    }

    private fun writeOutputs(plan: GenerationPlan, directory: Path, overwrite: Boolean) {
        val completed = mutableListOf<Path>()
        var writing: Path? = null
        try {
            Files.createDirectories(directory)
            plan.outputs.forEach { output ->
                val contents = output.prompts.joinToString(System.lineSeparator())
                val options = if (overwrite) {
                    arrayOf(StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)
                } else {
                    arrayOf(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
                }
                writing = output.path
                Files.writeString(output.path, contents, StandardCharsets.UTF_8, *options)
                completed.add(output.path)
                writing = null
            }
        } catch (exception: IOException) {
            val affected = (completed + listOfNotNull(writing)).joinToString().ifEmpty { "none" }
            throw PromptGeneratorException(
                "Could not write output: ${exception.message}. Completed or partial files: $affected.",
                exception,
            )
        }
    }

    private fun sanitizeFileName(name: String): String {
        var sanitized = name.replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('.', ' ', '_')
        if (sanitized.isEmpty()) sanitized = "template"
        if (sanitized.substringBefore('.').uppercase(Locale.ROOT) in WINDOWS_RESERVED_NAMES) {
            sanitized = "_$sanitized"
        }
        return sanitized
    }

    private fun readUtf8(path: Path): String {
        val bytes = try {
            Files.readAllBytes(path)
        } catch (exception: IOException) {
            throw PromptGeneratorException("Could not read '${path}': ${exception.message}", exception)
        }

        return try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
                .removePrefix("\uFEFF")
        } catch (exception: CharacterCodingException) {
            throw PromptGeneratorException("Input '${path}' is not valid UTF-8.", exception)
        }
    }

    private companion object {
        const val CHARACTER_TOKEN = "{{character}}"
        val WINDOWS_RESERVED_NAMES = setOf("CON", "PRN", "AUX", "NUL") +
            (1..9).map { "COM$it" } + (1..9).map { "LPT$it" }
    }
}
