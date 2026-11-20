package com.promptgenerator

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PromptGeneratorTest {
    @Test
    fun `generates one line per cleaned character and supports selected templates`() {
        val directory = Files.createTempDirectory("prompt-generator")
        val characters = directory.resolve("characters.txt")
        val templates = directory.resolve("templates.json")
        val output = directory.resolve("out")
        Files.writeString(characters, "\uFEFF # comment\n Alice \n\nBob\n", StandardCharsets.UTF_8)
        Files.writeString(
            templates,
            """{"metadata":"ignored","templates":{"basic":{"prompt":"A portrait of {{character}}","notes":"ignored"},"other":{"prompt":"A pose: {{character}}"}}}""",
        )

        val plan = PromptGenerator().generate(
            GeneratorOptions(characters, templates, output, selectedTemplates = listOf("other", "other")),
        )

        assertEquals(2, plan.totalPrompts)
        assertEquals(listOf("A pose: Alice", "A pose: Bob"), Files.readAllLines(output.resolve("other.txt")))
        assertFalse(Files.exists(output.resolve("basic.txt")))
    }

    @Test
    fun `dry run validates and reports paths without creating files`() {
        val (characters, templates, output) = fixture(
            """{"templates":{"default":{"prompt":"{{character}}"},"alternate":{"prompt":"Alt: {{character}}"}}}""",
        )

        val plan = PromptGenerator().generate(
            GeneratorOptions(characters, templates, output, dryRun = true),
        )

        assertEquals(4, plan.totalPrompts)
        assertEquals(listOf("default.txt", "alternate.txt"), plan.outputs.map { it.path.fileName.toString() })
        assertFalse(Files.exists(output))
    }

    @Test
    fun `validation failure leaves all destinations untouched`() {
        val (characters, templates, output) =
            fixture("""{"templates":{"first":{"prompt":"{{character}}"},"second":{"prompt":"invalid"}}}""")

        assertFailsWith<PromptGeneratorException> {
            PromptGenerator().generate(GeneratorOptions(characters, templates, output))
        }

        assertFalse(Files.exists(output))
    }

    @Test
    fun `refuses existing files unless overwrite is enabled`() {
        val (characters, templates, output) = fixture("""{"templates":{"basic":{"prompt":"{{character}}"}}}""")
        Files.createDirectories(output)
        val destination = output.resolve("basic.txt")
        Files.writeString(destination, "keep me")

        assertFailsWith<PromptGeneratorException> {
            PromptGenerator().generate(GeneratorOptions(characters, templates, output))
        }
        assertEquals("keep me", Files.readString(destination))

        PromptGenerator().generate(GeneratorOptions(characters, templates, output, overwrite = true))
        assertEquals(listOf("Alice", "Bob"), Files.readAllLines(destination))
    }

    @Test
    fun `rejects case insensitive filename collisions`() {
        val (characters, templates, output) =
            fixture("""{"templates":{"Portrait":{"prompt":"{{character}}"},"portrait":{"prompt":"{{character}}"}}}""")

        assertFailsWith<PromptGeneratorException> {
            PromptGenerator().generate(GeneratorOptions(characters, templates, output))
        }

        assertFalse(Files.exists(output))
    }

    @Test
    fun `rejects malformed UTF-8`() {
        val (characters, templates, output) = fixture("""{"templates":{"basic":{"prompt":"{{character}}"}}}""")
        Files.write(characters, byteArrayOf(0xC3.toByte(), 0x28))

        assertFailsWith<PromptGeneratorException> {
            PromptGenerator().generate(GeneratorOptions(characters, templates, output))
        }
        assertFalse(Files.exists(output))
    }

    private fun fixture(templateJson: String): Triple<Path, Path, Path> {
        val directory = Files.createTempDirectory("prompt-generator")
        val characters = directory.resolve("characters.txt")
        val templates = directory.resolve("templates.json")
        Files.writeString(characters, "Alice\nBob\n")
        Files.writeString(templates, templateJson)
        return Triple(characters, templates, directory.resolve("generated"))
    }
}
