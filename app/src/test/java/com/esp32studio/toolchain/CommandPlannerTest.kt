package com.esp32studio.toolchain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

class CommandPlannerTest {
    @Test
    fun runnerReturnsExitCodeAndOutput() {
        val result = CommandRunner().run(
            listOf("/system/bin/sh", "-c", "printf hello"),
            timeoutMs = 5_000
        )
        assertEquals(0, result.exitCode)
        assertEquals("hello", result.stdout)
        assertEquals("", result.stderr)
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
