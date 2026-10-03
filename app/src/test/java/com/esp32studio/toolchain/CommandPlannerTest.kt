package com.esp32studio.toolchain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

class CommandPlannerTest {
    @Test
    fun runnerReportsMissingCommand() {
        val result = CommandRunner().run(
            listOf("__esp32_studio_missing_command__"),
            timeoutMs = 5_000
        )
        assertEquals(127, result.exitCode)
    }

    @Test
    fun missingArduinoCliIsNotReportedReady() {
        val toolchain = ArduinoToolchain(
            runner = CommandRunner(),
            config = ArduinoConfig(
                cliBinary = File("/definitely/missing/arduino-cli"),
                fqbn = "esp32:esp32:esp32"
            )
        )
        assertFalse(toolchain.installed())
    }
}
