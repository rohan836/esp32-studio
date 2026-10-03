package com.esp32studio.toolchain

import android.content.Context
import java.io.File

class ArduinoToolchainManager(context: Context) {
    private val app = context.applicationContext
    private val root = File(app.filesDir, "arduino")
    private val configFile = File(app.filesDir, "arduino-cli.yaml")
    private val cli = File(app.applicationInfo.nativeLibraryDir, "libarduino_cli.so")
    private val runner = CommandRunner()

    val projectFqbn: String = "esp32:esp32:esp32"

    fun runtimeAvailable(): Boolean = cli.isFile && cli.canExecute()

    fun prepareConfig(): File {
        File(root, "data").mkdirs()
        File(root, "downloads").mkdirs()
        File(root, "libraries").mkdirs()
        File(root, "staging").mkdirs()
        if (!configFile.isFile) {
            configFile.writeText(
                """
                board_manager:
                  additional_urls:
                    - https://espressif.github.io/arduino-esp32/package_esp32_index.json
                directories:
                  data: "${File(root, "data").absolutePath}"
                  downloads: "${File(root, "downloads").absolutePath}"
                  user: "${root.absolutePath}"
                """.trimIndent() + "\n"
            )
        }
        return configFile
    }

    fun status(): String {
        if (!runtimeAvailable()) {
            return "Android Arduino CLI is not packaged in this APK. Build the APK through the Android CI workflow."
        }
        val result = run(listOf("version"), timeoutMs = 10_000)
        return if (result.exitCode == 0) result.stdout.trim()
        else "Arduino CLI could not start: " + result.stderr.trim()
    }

    fun installEsp32Core(): CommandResult {
        check(runtimeAvailable()) {
            "Android Arduino CLI is not packaged in this APK. Build the APK through Android CI."
        }
        prepareConfig()
        val update = run(listOf("core", "update-index"), timeoutMs = 5 * 60 * 1000L)
        if (update.exitCode != 0) return update
        return run(listOf("core", "install", "esp32:esp32"), timeoutMs = 30 * 60 * 1000L)
    }

    fun compile(projectDirectory: File): CommandResult {
        check(runtimeAvailable()) {
            "Android Arduino CLI is not packaged in this APK. Build the APK through Android CI."
        }
        require(projectDirectory.isDirectory) { "Project directory does not exist." }
        prepareConfig()
        val output = File(projectDirectory, "build")
        output.mkdirs()
        return run(
            listOf(
                "compile",
                "--fqbn", projectFqbn,
                "--output-dir", output.absolutePath,
                "--export-binaries",
                projectDirectory.absolutePath
            ),
            timeoutMs = 30 * 60 * 1000L
        )
    }

    fun compiledSketchImage(projectDirectory: File): File? {
        val sketchName = projectDirectory.listFiles()
            .orEmpty()
            .firstOrNull { it.isFile && it.extension.equals("ino", true) }
            ?.nameWithoutExtension ?: return null
        val build = File(projectDirectory, "build")
        return listOf(
            File(build, sketchName + ".ino.bin"),
            File(build, sketchName + ".bin")
        ).firstOrNull { it.isFile && it.length() > 0L }
    }

    private fun run(args: List<String>, timeoutMs: Long): CommandResult {
        val config = prepareConfig()
        return runner.run(
            listOf(cli.absolutePath, "--config-file", config.absolutePath) + args,
            workingDirectory = root,
            timeoutMs = timeoutMs,
            environment = mapOf("HOME" to app.filesDir.absolutePath)
        )
    }
}
