package com.esp32studio.toolchain

import org.junit.Assert.assertEquals
import org.junit.Test

class CommandRunnerTest {
    @Test
    fun timesOutLongRunningProcess() {
        val result = CommandRunner().run(
            listOf("sh", "-c", "sleep 2"),
            timeoutMs = 100
        )
        assertEquals(124, result.exitCode)
    }

    @Test
    fun capturesBothOutputStreams() {
        val result = CommandRunner().run(
            listOf("sh", "-c", "printf out; printf err >&2")
        )
        assertEquals(0, result.exitCode)
        assertEquals("out", result.stdout)
        assertEquals("err", result.stderr)
    }
}
