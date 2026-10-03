package com.esp32studio.ui

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.esp32studio.cli.CliParser
import com.esp32studio.flash.Esp32Bootloader
import com.esp32studio.library.ArduinoLibraryManager
import com.esp32studio.device.DeviceInfo
import com.esp32studio.device.UsbDeviceRepository
import com.esp32studio.project.ProjectStore
import com.esp32studio.serial.SerialSession
import com.esp32studio.toolchain.CommandRunner
import com.esp32studio.toolchain.ArduinoToolchainManager
import java.util.concurrent.Executors

class MainActivity : android.app.Activity() {

    companion object {
        private const val ACTION_USB_PERMISSION = "com.esp32studio.USB_PERMISSION"
    }

    private lateinit var usbManager: UsbManager
    private lateinit var deviceRepository: UsbDeviceRepository
    private lateinit var projectStore: ProjectStore
    private lateinit var libraryManager: ArduinoLibraryManager
    private lateinit var toolchainManager: ArduinoToolchainManager

    private val background = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    private var selectedDevice: UsbDevice? = null
    private var serialSession: SerialSession? = null
    private var permissionRequestedKey: String? = null

    private lateinit var statusText: TextView
    private lateinit var deviceText: TextView
    private lateinit var editor: EditText
    private lateinit var terminal: EditText
    private lateinit var console: TextView
    private lateinit var connectButton: Button
    private lateinit var runButton: Button

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    refreshDevices()
                }

                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    permissionRequestedKey = null
                    selectedDevice = null
                    serialSession?.shutdown()
                    serialSession = null
                    statusText.text = "Disconnected"
                    refreshDevices()
                }

                ACTION_USB_PERMISSION -> {
                    val device = intent.deviceExtra()
                    val granted = intent.getBooleanExtra(
                        UsbManager.EXTRA_PERMISSION_GRANTED,
                        false
                    )

                    permissionRequestedKey = null

                    if (granted && device != null) {
                        selectedDevice = device
                        connectToDevice(device)
                    } else {
                        appendConsole("USB access was not granted.")
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        usbManager = getSystemService(USB_SERVICE) as UsbManager
        deviceRepository = UsbDeviceRepository(usbManager)
        projectStore = ProjectStore(this)
        libraryManager = ArduinoLibraryManager(this)
        toolchainManager = ArduinoToolchainManager(this)

        registerUsbReceiver()
        setContentView(buildUi())

        editor.setText(projectStore.load())
        refreshDevices()

        appendConsole("ESP32 Studio ready.")
        appendConsole("Connect an ESP32 USB cable to start automatic detection.")
    }

    override fun onDestroy() {
        serialSession?.shutdown()
        background.shutdownNow()
        runCatching { unregisterReceiver(usbReceiver) }
        super.onDestroy()
    }

    private fun buildUi(): android.view.View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 18, 20, 18)
            setBackgroundColor(0xFFF6F7F9.toInt())
        }

        statusText = TextView(this).apply {
            text = "Starting..."
            textSize = 18f
        }

        deviceText = TextView(this).apply {
            text = "No board detected"
            textSize = 14f
            setPadding(0, 8, 0, 12)
        }

        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        connectButton = Button(this).apply {
            text = "Connect"
            setOnClickListener {
                selectedDevice?.let { requestUsbPermissionOrConnect(it) }
            }
        }

        val saveButton = Button(this).apply {
            text = "Save"
            setOnClickListener {
                projectStore.save(editor.text.toString())
                appendConsole("Project saved.")
            }
        }

        runButton = Button(this).apply {
            text = "Run"
            isEnabled = false
            setOnClickListener { saveAndRun() }
        }

        toolbar.addView(connectButton, LinearLayout.LayoutParams(0, 52.dp(), 1f))
        toolbar.addView(saveButton, LinearLayout.LayoutParams(0, 52.dp(), 1f))
        toolbar.addView(runButton, LinearLayout.LayoutParams(0, 52.dp(), 1f))

        editor = EditText(this).apply {
            setTextSize(13f)
            gravity = Gravity.TOP or Gravity.START
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            isSingleLine = false
            setPadding(12, 12, 12, 12)
            setBackgroundColor(0xFFFFFFFF.toInt())
            hint = "hello-esp32.ino"
            minLines = 12
        }

        val terminalLabel = TextView(this).apply {
            text = "Terminal"
            textSize = 14f
            setPadding(0, 12, 0, 6)
        }

        terminal = EditText(this).apply {
            hint = "echo hello"
            setSingleLine(true)
            textSize = 13f
        }

        val terminalButton = Button(this).apply {
            text = "Execute"
            setOnClickListener {
                executeStudioCommand(terminal.text.toString())
            }
        }

        console = TextView(this).apply {
            textSize = 12f
            setTextIsSelectable(true)
            setPadding(12, 12, 12, 12)
            setBackgroundColor(0xFF111318.toInt())
            setTextColor(0xFFE7EAF0.toInt())
        }

        val consoleScroll = ScrollView(this).apply { addView(console) }

        root.addView(statusText, ViewGroup.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(deviceText, ViewGroup.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(toolbar, ViewGroup.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))

        root.addView(
            editor,
            LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = 10.dp() }
        )

        root.addView(terminalLabel)

        val terminalRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(terminal, LinearLayout.LayoutParams(0, 50.dp(), 1f))
            addView(terminalButton, LinearLayout.LayoutParams(100.dp(), 50.dp()))
        }

        root.addView(terminalRow)
        root.addView(
            consoleScroll,
            LinearLayout.LayoutParams(-1, 180.dp()).apply { topMargin = 8.dp() }
        )

        return root
    }

    private fun refreshDevices() {
        background.execute {
            val devices = runCatching { deviceRepository.scan() }.getOrDefault(emptyList())
            main.post { updateDeviceUi(devices) }
        }
    }

    private fun updateDeviceUi(devices: List<DeviceInfo>) {
        val preferred = devices.firstOrNull {
            it.confidence == DeviceInfo.Confidence.HIGH
        } ?: devices.firstOrNull()

        if (preferred == null) {
            selectedDevice = null
            deviceText.text = "No supported USB serial device detected"
            statusText.text = "Ready"
            connectButton.isEnabled = false
            runButton.isEnabled = true
            return
        }

        deviceText.text = buildString {
            append(preferred.boardFamily ?: "USB serial device")
            append(" · VID 0x%04X PID 0x%04X".format(preferred.vendorId, preferred.productId))
            append(" · ")
            append(preferred.portCount)
            append(" port(s)")
            preferred.manufacturer?.let {
                append(" · ")
                append(it)
            }
        }

        selectedDevice = findAndroidDevice(preferred.key)

        statusText.text = when (preferred.confidence) {
            DeviceInfo.Confidence.HIGH -> "ESP32 detected"
            DeviceInfo.Confidence.MEDIUM -> "USB serial detected"
            DeviceInfo.Confidence.LOW -> "Device detected"
        }

        connectButton.isEnabled = selectedDevice != null
        runButton.isEnabled = true

        selectedDevice?.let { requestUsbPermissionOrConnect(it) }
    }

    private fun findAndroidDevice(key: String): UsbDevice? {
        return usbManager.deviceList.values.firstOrNull { device ->
            device.vendorId.toString() + ":" + device.productId + ":" + device.deviceId == key
        }
    }

    private fun requestUsbPermissionOrConnect(device: UsbDevice) {
        if (usbManager.hasPermission(device)) {
            connectToDevice(device)
            return
        }

        val key = device.vendorId.toString() + ":" +
            device.productId + ":" + device.deviceId

        if (permissionRequestedKey == key) return
        permissionRequestedKey = key

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE
        } else {
            0
        }

        val intent = Intent(ACTION_USB_PERMISSION).setPackage(packageName)
        val permissionIntent = PendingIntent.getBroadcast(this, 0, intent, flags)

        usbManager.requestPermission(device, permissionIntent)
        appendConsole("Requesting USB access for " + device.deviceName + "...")
    }

    private fun connectToDevice(device: UsbDevice) {
        serialSession?.shutdown()

        serialSession = SerialSession(
            usbManager = usbManager,
            onText = { text -> main.post { appendConsole(text.trimEnd()) } },
            onClosed = { error ->
                main.post {
                    statusText.text = "Disconnected"
                    appendConsole(
                        if (error.isNullOrBlank()) {
                            "Serial session closed."
                        } else {
                            "Serial error: " + error
                        }
                    )
                }
            }
        )

        serialSession?.connect(device)
        selectedDevice = device
        statusText.text = "Connected"
        appendConsole("Connected to " + device.deviceName + " at 115200 baud.")
    }

    private fun saveAndRun() {
        runBuildFlashMonitor()
    }

    private fun executeStudioCommand(input: String) {
        val command = CliParser.parse(input) ?: return
        when (command.name) {
            "help" -> appendConsole("ESP32 Studio CLI\nesp devices | esp detect | esp info | esp doctor\nesp project path | esp build | esp flash FILE [ADDRESS] | esp run\nesp monitor | esp lib search NAME | esp lib add NAME | esp lib list | esp toolchain install")
            "devices" -> background.execute {
                val devices = runCatching { deviceRepository.scan() }.getOrDefault(emptyList())
                val output = if (devices.isEmpty()) "No supported USB serial device found." else devices.joinToString("\n") { d ->
                    "${d.boardFamily ?: "USB serial"} VID=0x%04X PID=0x%04X ports=%d confidence=%s".format(d.vendorId, d.productId, d.portCount, d.confidence)
                }
                main.post { appendConsole(output) }
            }
            "info" -> runBootloaderProbe()
            "doctor" -> runDoctor()
            "project" -> when (command.arguments.firstOrNull()) {
                "path" -> appendConsole(projectStore.path().absolutePath)
                "save" -> { projectStore.save(editor.text.toString()); appendConsole("Project saved.") }
                else -> appendConsole("Usage: esp project path | esp project save")
            }
            "build" -> runBuildCommand()
            "flash" -> runFlashCommand(command.arguments)
            "run" -> runBuildFlashMonitor()
            "monitor" -> appendConsole(if (serialSession == null) "No serial session connected." else "Serial monitor is active in the console.")
            "lib" -> runLibraryCommand(command.arguments)
            "toolchain" -> runToolchainCommand(command.arguments)
            "clear" -> console.text = ""
            else -> executeShellCommand(input)
        }
    }

    private fun runLibraryCommand(arguments: List<String>) {
        val action = arguments.firstOrNull()?.lowercase()
        val name = arguments.drop(1).joinToString(" ").trim()
        when (action) {
            "search" -> {
                if (name.isBlank()) {
                    appendConsole("Usage: esp lib search NAME")
                    return
                }
                appendConsole("Searching Arduino Library Registry for: " + name)
                background.execute {
                    val result = runCatching { libraryManager.search(name, 15) }
                    main.post {
                        result.onSuccess { libraries ->
                            if (libraries.isEmpty()) {
                                appendConsole("No matching libraries found.")
                            } else {
                                appendConsole(libraries.joinToString("\n") {
                                    it.name + " " + it.version + " — " + it.sentence
                                })
                                appendConsole("Install with: esp lib add \"" + libraries.first().name + "\"")
                            }
                        }.onFailure {
                            appendConsole("Library search failed: " + it.message)
                        }
                    }
                }
            }
            "add", "install" -> {
                if (name.isBlank()) {
                    appendConsole("Usage: esp lib add LIBRARY_NAME")
                    return
                }
                appendConsole("Installing library: " + name)
                background.execute {
                    val result = runCatching { libraryManager.install(name) }
                    main.post {
                        result.onSuccess { appendConsole("Library installed: " + it.absolutePath) }
                            .onFailure { appendConsole("Library install failed: " + it.message) }
                    }
                }
            }
            "list" -> background.execute {
                val installed = runCatching { libraryManager.installedLibraries() }
                main.post {
                    installed.onSuccess {
                        appendConsole(if (it.isEmpty()) "No libraries installed." else it.joinToString("\n"))
                    }.onFailure { appendConsole("Could not list libraries: " + it.message) }
                }
            }
            else -> appendConsole("Usage: esp lib search NAME | esp lib add NAME | esp lib list")
        }
    }

    private fun runBootloaderProbe() {
        val device = selectedDevice
        if (device == null) {
            appendConsole("No ESP32 USB device is selected.")
            return
        }

        serialSession?.shutdown()
        serialSession = null
        statusText.text = "Probing ESP32 bootloader..."
        appendConsole("Probing ${device.deviceName} at 115200...")
        background.execute {
            val result = runCatching {
                Esp32Bootloader(usbManager).probe(device)
            }
            main.post {
                result.onSuccess {
                    statusText.text = "ESP32 bootloader ready"
                    appendConsole("ESP32 UART bootloader responded.")
                }.onFailure {
                    statusText.text = "Probe failed"
                    appendConsole("ESP32 probe failed: ${it.message}")
                }
                connectToDevice(device)
            }
        }
    }

    private fun runFlashCommand(arguments: List<String>) {
        val device = selectedDevice
        if (device == null) {
            appendConsole("No ESP32 USB device is selected.")
            return
        }

        val fileName = arguments.firstOrNull() ?: "firmware.bin"
        val addressText = arguments.getOrNull(1) ?: "0x10000"
        val address = parseFlashAddress(addressText)

        if (address == null) {
            appendConsole("Invalid flash address: $addressText")
            return
        }

        val image = safeProjectFile(fileName)
        if (image == null) {
            appendConsole("Flash file must stay inside the project directory: $fileName")
            return
        }

        if (!image.exists()) {
            appendConsole("Firmware image not found: ${image.relativeTo(projectStore.path())}")
            return
        }

        if (!image.name.endsWith(".bin", ignoreCase = true)) {
            appendConsole("Only .bin firmware images can be flashed.")
            return
        }

        if (address % 0x1000L != 0L) {
            appendConsole("Flash address must be 0x1000-aligned.")
            return
        }

        serialSession?.shutdown()
        serialSession = null
        statusText.text = "Flashing..."
        appendConsole("Flashing ${image.name} at $addressText...")

        background.execute {
            val result = runCatching {
                Esp32Bootloader(
                    usbManager = usbManager,
                    onProgress = { sent, total ->
                        main.post {
                            appendConsole("Flash ${sent}/${total} bytes")
                        }
                    }
                ).flash(device, image, address)
            }

            main.post {
                result.onSuccess {
                    statusText.text = "Flash complete"
                    appendConsole("Flash complete. Reconnecting serial monitor...")
                }.onFailure {
                    statusText.text = "Flash failed"
                    appendConsole("Flash failed: ${it.message}")
                }
                connectToDevice(device)
            }
        }
    }

    private fun runBuildFlashMonitor() {
        projectStore.save(editor.text.toString())
        runBuildCommand(flashAfterBuild = true)
    }

    private fun runBuildCommand(flashAfterBuild: Boolean = false) {
        projectStore.save(editor.text.toString())
        appendConsole("Saved sketch. Checking Android Arduino toolchain...")
        background.execute {
            val result = runCatching {
                val setup = toolchainManager.ensureEsp32CoreInstalled()
                if (setup.exitCode != 0) {
                    setup
                } else {
                    val build = toolchainManager.compile(projectStore.path())
                    com.esp32studio.toolchain.CommandResult(
                        build.exitCode,
                        setup.stdout + "\n" + build.stdout,
                        setup.stderr + "\n" + build.stderr
                    )
                }
            }
            main.post {
                result.onSuccess { build ->
                    if (build.stdout.isNotBlank()) appendConsole(build.stdout.trim())
                    if (build.stderr.isNotBlank()) appendConsole(build.stderr.trim())
                    if (build.exitCode != 0) {
                        appendConsole("Build/setup failed (exit " + build.exitCode + ").")
                        return@post
                    }
                    appendConsole("Build completed.")
                    if (flashAfterBuild) {
                        val image = toolchainManager.compiledSketchImage(projectStore.path())
                        if (image == null) {
                            appendConsole("Build succeeded but the sketch .bin was not found.")
                        } else {
                            val relative = projectStore.path().toPath().relativize(image.toPath()).toString()
                            runFlashCommand(listOf(relative, "0x10000"))
                        }
                    }
                }.onFailure {
                    appendConsole("Build unavailable: " + it.message)
                }
            }
        }
    }

    private fun runToolchainCommand(arguments: List<String>) {
        when (arguments.firstOrNull()?.lowercase()) {
            "status" -> background.execute {
                val status = runCatching { toolchainManager.status() }
                    .getOrElse { "Toolchain status failed: " + it.message }
                main.post { appendConsole(status) }
            }
            "install" -> {
                appendConsole("Installing ESP32 Arduino core. This downloads the compiler packages.")
                background.execute {
                    val result = runCatching { toolchainManager.installEsp32Core() }
                    main.post {
                        result.onSuccess {
                            appendConsole(it.stdout)
                            if (it.stderr.isNotBlank()) appendConsole(it.stderr)
                            appendConsole(if (it.exitCode == 0) "ESP32 core install finished." else "ESP32 core install failed.")
                        }.onFailure {
                            appendConsole("Toolchain install failed: " + it.message)
                        }
                    }
                }
            }
            else -> appendConsole("Usage: esp toolchain status | esp toolchain install")
        }
    }

    private fun runDoctor() {
        background.execute {
            val devices = runCatching { deviceRepository.scan() }.getOrDefault(emptyList())
            val projectPath = projectStore.path().absolutePath
            val selected = selectedDevice?.deviceName ?: "none"
            val output = buildString {
                appendLine("ESP32 Studio Doctor")
                appendLine("USB candidates: ${devices.size}")
                appendLine("Selected device: $selected")
                appendLine("Project: $projectPath")
                appendLine("Native ESP bootloader: available")
                appendLine("Arduino toolchain: " + toolchainManager.status())
                appendLine("Installed libraries: " + libraryManager.installedLibraries().size)
                appendLine("Serial monitor: ${if (serialSession == null) "inactive" else "active"}")
                if (devices.isNotEmpty()) {
                    devices.forEach {
                        appendLine("- ${it.boardFamily ?: "USB serial"} VID=0x%04X PID=0x%04X confidence=${it.confidence}".format(it.vendorId, it.productId))
                    }
                }
            }
            main.post { appendConsole(output.trimEnd()) }
        }
    }

    private fun parseFlashAddress(value: String): Long? {
        return runCatching {
            if (value.startsWith("0x", ignoreCase = true)) {
                value.substring(2).toLong(16)
            } else {
                value.toLong()
            }
        }.getOrNull()
    }

    private fun safeProjectFile(relativePath: String): java.io.File? {
        val root = projectStore.path().canonicalFile
        val target = java.io.File(root, relativePath).canonicalFile
        return target.takeIf { it.path == root.path || it.path.startsWith(root.path + java.io.File.separator) }
    }

    private fun executeShellCommand(command: String) {
        if (command.isBlank()) return
        background.execute {
            val result = CommandRunner().run(listOf("/system/bin/sh", "-c", command), workingDirectory = filesDir, timeoutMs = 30_000)
            main.post { appendConsole("$ " + command + "\n" + result.stdout + result.stderr + "\n[exit " + result.exitCode + "]") }
        }
    }


    private fun appendConsole(text: String) {
        console.append(text + "\n")
        (console.parent as? ScrollView)?.post {
            (console.parent as ScrollView).fullScroll(ScrollView.FOCUS_DOWN)
        }
    }

    private fun registerUsbReceiver() {
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(ACTION_USB_PERMISSION)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(usbReceiver, filter)
        }
    }

    private fun Int.dp(): Int =
        (this * resources.displayMetrics.density).toInt()

    private fun Intent.deviceExtra(): UsbDevice? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(UsbManager.EXTRA_DEVICE)
        }
    }
}
