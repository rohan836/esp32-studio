package com.esp32studio.toolchain

import java.io.File
import java.io.IOException
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
        return try {
            val process = ProcessBuilder(command)
                .directory(workingDirectory)
                .redirectErrorStream(false)
                .apply { environment().putAll(environment) }
                .start()

            val stdout = process.inputStream.bufferedReader().use { it.readText() }
            val stderr = process.errorStream.bufferedReader().use { it.readText() }

            if (!process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly()
                CommandResult(124, stdout, stderr + "\nCommand timed out.")
            } else {
                CommandResult(process.exitValue(), stdout, stderr)
            }
        } catch (error: IOException) {
            CommandResult(127, "", error.message.orEmpty())
        }
    }
}
