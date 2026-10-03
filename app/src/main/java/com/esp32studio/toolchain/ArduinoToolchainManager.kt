package com.esp32studio.toolchain

import android.content.Context
import java.io.File

class ArduinoToolchainManager(context: Context) {
    private val app = context.applicationContext
    private val root = File(app.filesDir, "arduino")
    private val configFile = File(app.filesDir, "arduino-cli.yaml")
    private val cli = File(app.applicationInfo.nativeLibraryDir, "libarduino_cli.so")
    private val runner = CommandRunner()

    val defaultFqbn: String = "esp32:esp32:esp32"

    fun runtimeAvailable(): Boolean = cli.isFile && cli.canExecute()

    fun prepareConfig(): File {
        File(root, "data").mkdirs()
        File(root, "downloads").mkdirs()
        File(root, "libraries").mkdirs()
        File(root, "staging").mkdirs()
        if (!configFile.isFile) {
            configFile.writeText(
                "board_manager:\n" +
                    "  additional_urls:\n" +
                    "    - https://espressif.github.io/arduino-esp32/package_esp32_index.json\n" +
                    "directories:\n" +
                    "  data: \"" + File(root, "data").absolutePath + "\"\n" +
                    "  downloads: \"" + File(root, "downloads").absolutePath + "\"\n" +
                    "  user: \"" + root.absolutePath + "\"\n"
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

    fun boardListAll(query: String = ""): CommandResult {
        return run(
            if (query.isBlank()) listOf("board", "listall")
            else listOf("board", "listall", query),
            timeoutMs = 30_000
        )
    }

    fun coreList(): CommandResult =
        run(listOf("core", "list"), timeoutMs = 30_000)

    fun coreSearch(query: String): CommandResult =
        run(listOf("core", "search", query), timeoutMs = 30_000)

    fun installCore(coreId: String): CommandResult {
        require(coreId.isNotBlank()) { "Core ID must not be blank." }
        prepareConfig()
        val update = run(listOf("core", "update-index"), timeoutMs = 5 * 60 * 1000L)
        if (update.exitCode != 0) return update
        return run(listOf("core", "install", coreId), timeoutMs = 30 * 60 * 1000L)
    }

    fun ensureCoreInstalled(fqbn: String): CommandResult {
        BoardFqbn.validate(fqbn)
        check(runtimeAvailable()) {
            "Android Arduino CLI is not packaged in this APK. Build the APK through the Android CI workflow."
        }
        prepareConfig()
        val coreId = BoardFqbn.coreId(fqbn)
        val listed = coreList()
        if (listed.exitCode == 0 && listed.stdout.lineSequence().any { it.trim().startsWith(coreId) }) {
            return CommandResult(0, "Core already installed: " + coreId, "")
        }
        return installCore(coreId)
    }

    fun compile(projectDirectory: File, fqbn: String): CommandResult {
        BoardFqbn.validate(fqbn)
        check(runtimeAvailable()) {
            "Android Arduino CLI is not packaged in this APK. Build the APK through the Android CI workflow."
        }
        require(projectDirectory.isDirectory) { "Project directory does not exist." }
        prepareConfig()
        val output = File(projectDirectory, "build")
        output.mkdirs()
        return run(
            listOf(
                "compile",
                "--fqbn", fqbn,
                "--output-dir", output.absolutePath,
                "--export-binaries",
                projectDirectory.absolutePath
            ),
            timeoutMs = 30 * 60 * 1000L
        )
    }

    fun compiledSketchArtifact(projectDirectory: File, fqbn: String): File? {
        val sketchName = projectDirectory.listFiles()
            .orEmpty()
            .firstOrNull { it.isFile && it.extension.equals("ino", true) }
            ?.nameWithoutExtension ?: return null
        val build = File(projectDirectory, "build")
        val extension = BoardFqbn.preferredArtifactExtension(fqbn)
        return build.walkTopDown()
            .filter { it.isFile && it.extension.equals(extension, true) }
            .firstOrNull { file ->
                file.name.startsWith(sketchName, ignoreCase = true)
            }
            ?: build.walkTopDown()
                .filter { it.isFile && it.extension.equals(extension, true) }
                .firstOrNull()
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
