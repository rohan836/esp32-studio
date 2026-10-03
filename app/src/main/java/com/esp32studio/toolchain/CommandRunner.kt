package com.esp32studio.toolchain

import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
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
        require(command.isNotEmpty()) { "Command must not be empty." }

        return try {
            val process = ProcessBuilder(command)
                .directory(workingDirectory)
                .redirectErrorStream(false)
                .apply { environment().putAll(environment) }
                .start()

            val outputExecutor = Executors.newFixedThreadPool(2)
            val latch = CountDownLatch(2)

            var stdout = ""
            var stderr = ""

            outputExecutor.execute {
                try {
                    stdout = process.inputStream.bufferedReader().use { it.readText() }
                } finally {
                    latch.countDown()
                }
            }

            outputExecutor.execute {
                try {
                    stderr = process.errorStream.bufferedReader().use { it.readText() }
                } finally {
                    latch.countDown()
                }
            }

            val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroy()
                if (!process.waitFor(250, TimeUnit.MILLISECONDS)) {
                    process.destroyForcibly()
                }
                latch.await(1, TimeUnit.SECONDS)
                CommandResult(124, stdout, stderr + "\nCommand timed out.")
            } else {
                latch.await(1, TimeUnit.SECONDS)
                CommandResult(process.exitValue(), stdout, stderr)
            }.also {
                outputExecutor.shutdownNow()
            }
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            CommandResult(130, "", "Command interrupted.")
        } catch (error: IOException) {
            CommandResult(127, "", error.message.orEmpty())
        }
    }
}
