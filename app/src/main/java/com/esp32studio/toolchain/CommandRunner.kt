package com.esp32studio.toolchain

import java.io.File
import java.util.concurrent.TimeUnit

data class CommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String
)

class CommandRunner {
    fun run(
        command: List<String>,
        workingDirectory: File? = null,
        timeoutMs: Long = 120_000,
        environment: Map<String, String> = emptyMap()
    ): CommandResult {
        val process = ProcessBuilder(command)
            .directory(workingDirectory)
            .redirectErrorStream(false)
            .apply { environment().putAll(environment) }
            .start()

        val stdout = process.inputStream.bufferedReader().use { it.readText() }
        val stderr = process.errorStream.bufferedReader().use { it.readText() }

        if (!process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
            process.destroyForcibly()
            return CommandResult(124, stdout, stderr + "
Command timed out.")
        }

        return CommandResult(process.exitValue(), stdout, stderr)
    }
}
