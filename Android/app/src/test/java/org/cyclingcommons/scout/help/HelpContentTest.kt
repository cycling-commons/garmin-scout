package org.cyclingcommons.scout.help

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class HelpContentTest {
    @Test
    fun parsesExampleTemplate() {
        val json = Files.readString(Paths.get("help", "help.example.json"))
        val page = HelpContent.parse(JSONObject(json))
        assertEquals("How Scout works", page.title)
        assertTrue(page.sections.isNotEmpty())
        val sharing = page.sections.first { it.heading == "Sharing (optional)" }
        assertEquals("Sharing (optional)", sharing.heading)
        assertEquals(1, sharing.links.size)
        assertEquals("https://example.com", sharing.links.first().url)
    }

    @Test
    fun legalExampleIsEmpty() {
        val json = Files.readString(Paths.get("help", "legal.example.json"))
        val sections = HelpContent.parseLegalSections(JSONObject(json))
        assertTrue(sections.isEmpty())
    }

    @Test
    fun mergesLegalBeforeSourceCode() {
        val help = HelpContent.parse(JSONObject(Files.readString(Paths.get("help", "help.example.json"))))
        val legal = listOf(
            HelpSection(
                heading = "Safety & responsibility",
                body = listOf("Ride safely."),
            ),
        )
        val merged = HelpContent.mergeLegal(help, legal)
        val idx = merged.sections.indexOfFirst { it.heading == "Source code" }
        assertEquals("Safety & responsibility", merged.sections[idx - 1].heading)
    }
}
