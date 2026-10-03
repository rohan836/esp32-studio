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
import com.esp32studio.device.DeviceInfo
import com.esp32studio.device.UsbDeviceRepository
import com.esp32studio.project.ProjectStore
import com.esp32studio.serial.SerialSession
import com.esp32studio.toolchain.CommandRunner
import java.util.concurrent.Executors

class MainActivity : android.app.Activity() {

    companion object {
        private const val ACTION_USB_PERMISSION = "com.esp32studio.USB_PERMISSION"
    }

    private lateinit var usbManager: UsbManager
    private lateinit var deviceRepository: UsbDeviceRepository
    private lateinit var projectStore: ProjectStore

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
            runButton.isEnabled = false
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
        runButton.isEnabled = selectedDevice != null

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
        projectStore.save(editor.text.toString())
        appendConsole("Project saved.")
        appendConsole("Build/flash adapters are present, but an Android-compatible Arduino toolchain is not bundled in 0.1.")
        appendConsole("Next runtime step: install a compatible local toolchain or use the remote build backend.")
        Toast.makeText(
            this,
            "Project saved. Toolchain runtime is not installed yet.",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun executeStudioCommand(input: String) {
        val command = CliParser.parse(input) ?: return
        when (command.name) {
            "help" -> appendConsole("ESP32 Studio CLI\nesp devices | esp detect | esp info | esp doctor\nesp project path | esp build | esp flash | esp run\nesp monitor | esp lib search NAME | esp lib add NAME")
            "devices" -> background.execute {
                val devices = runCatching { deviceRepository.scan() }.getOrDefault(emptyList())
                val output = if (devices.isEmpty()) "No supported USB serial device found." else devices.joinToString("\n") { d ->
                    "${d.boardFamily ?: "USB serial"} VID=0x%04X PID=0x%04X ports=%d confidence=%s".format(d.vendorId, d.productId, d.portCount, d.confidence)
                }
                main.post { appendConsole(output) }
            }
            "info" -> appendConsole(selectedDevice?.let { "USB device: ${it.deviceName}\nVID=0x%04X PID=0x%04X".format(it.vendorId, it.productId) + "\nChip identity requires a bootloader probe." } ?: "No device connected.")
            "doctor" -> {
                val count = runCatching { deviceRepository.scan().size }.getOrDefault(0)
                appendConsole("ESP32 Studio Doctor\nUSB serial devices: $count\nProject: ${projectStore.path().absolutePath}\nArduino compiler: not installed\nFirmware flasher: not installed")
            }
            "project" -> when (command.arguments.firstOrNull()) {
                "path" -> appendConsole(projectStore.path().absolutePath)
                "save" -> { projectStore.save(editor.text.toString()); appendConsole("Project saved.") }
                else -> appendConsole("Usage: esp project path | esp project save")
            }
            "build" -> appendConsole("Build unavailable: Android-compatible Arduino compiler is not installed.")
            "flash" -> appendConsole("Flash unavailable: ESP bootloader flasher is not installed.")
            "run" -> { projectStore.save(editor.text.toString()); appendConsole("Project saved. Build and flash runtimes are not installed.") }
            "monitor" -> appendConsole(if (serialSession == null) "No serial session connected." else "Serial monitor is active in the console.")
            "lib" -> appendConsole("Library manager is not connected yet: esp lib ${command.arguments.joinToString(" ")}")
            "clear" -> console.text = ""
            else -> executeShellCommand(input)
        }
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
