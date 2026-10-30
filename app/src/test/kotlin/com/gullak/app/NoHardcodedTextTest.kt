package com.gullak.app

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/** Every user-facing string lives in strings.xml: this fails the build if a screen writes text directly. */
class NoHardcodedTextTest {

    private val literalText = Regex(
        """\b(?:Text|OneLineLabel|PillButton|OutlinePillButton|AmountChip|SuggestionBubble|StatusChip)\(\s*"|""" +
            """\b(?:text|title|contentDescription|placeholder) = "[^"]*\p{L}""",
    )

    @Test
    fun screensTakeTheirTextFromStringResources() {
        val root = File("..").canonicalFile
        val sources = listOf("feature", "core/designsystem", "app/src/main")
            .flatMap { File(root, it).walkTopDown() }
            .filter { it.extension == "kt" && "/src/main/" in it.invariantSeparatorsPath }

        val found = sources.flatMap { file ->
            literalText.findAll(file.readText()).map { "${file.name}: ${it.value}" }.toList()
        }

        assertEquals(true, sources.size > 10)
        assertEquals(emptyList<String>(), found)
    }
}
