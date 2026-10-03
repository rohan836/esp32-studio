package com.esp32studio.cli

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CliParserTest {
    @Test
    fun parsesEspCommandAndArguments() {
        assertEquals(
            CliCommand("lib", listOf("add", "FastLED")),
            CliParser.parse("esp lib add FastLED")
        )
    }

    @Test
    fun parsesQuotedArguments() {
        assertEquals(
            CliCommand("project", listOf("new", "my project")),
            CliParser.parse("esp project new \"my project\"")
        )
    }

    @Test
    fun mapsShortAliases() {
        assertEquals(CliCommand("devices", emptyList()), CliParser.parse("esp detect"))
        assertEquals(CliCommand("help", emptyList()), CliParser.parse("?"))
    }

    @Test
    fun ignoresEmptyInput() {
        assertNull(CliParser.parse("   "))
    }
}
