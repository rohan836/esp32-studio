package com.esp32studio.toolchain

import java.io.File

data class ArduinoConfig(
    val cliBinary: File,
    val fqbn: String
)

class ArduinoToolchain(
    private val runner: CommandRunner,
    private val config: ArduinoConfig
) {
    fun installed(): Boolean =
        config.cliBinary.exists() && config.cliBinary.canExecute()

    fun compile(projectDirectory: File): CommandResult {
        require(installed()) { "Arduino CLI is not installed for Android yet." }
        return runner.run(
            listOf(
                config.cliBinary.absolutePath,
                "compile",
                "--fqbn",
                config.fqbn,
                projectDirectory.absolutePath
            ),
            workingDirectory = projectDirectory
        )
    }

    fun upload(projectDirectory: File, port: String): CommandResult {
        require(installed()) { "Arduino CLI is not installed for Android yet." }
        return runner.run(
            listOf(
                config.cliBinary.absolutePath,
                "upload",
                "--fqbn",
                config.fqbn,
                "--port",
                port,
                projectDirectory.absolutePath
            ),
            workingDirectory = projectDirectory
        )
    }

    fun installLibrary(library: String): CommandResult {
        require(installed()) { "Arduino CLI is not installed for Android yet." }
        return runner.run(
            listOf(
                config.cliBinary.absolutePath,
                "lib",
                "install",
                library
            )
        )
    }
}
