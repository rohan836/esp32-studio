package com.esp32studio.serial

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class SerialSession(
    private val usbManager: UsbManager,
    private val onText: (String) -> Unit,
    private val onClosed: (String?) -> Unit
) {
    private val executor: ExecutorService = Executors.newFixedThreadPool(2)
    private val running = AtomicBoolean(false)
    private var port: UsbSerialPort? = null

    fun connect(device: UsbDevice, baudRate: Int = 115200) {
        executor.execute {
            try {
                val driver = UsbSerialProber.getDefaultProber().probeDevice(device)
                    ?: error("No USB serial driver found for " + device.deviceName)

                check(usbManager.hasPermission(device)) {
                    "Android USB permission is not granted."
                }

                val connection = usbManager.openDevice(device)
                    ?: error("Android could not open " + device.deviceName)

                val candidate = driver.ports.firstOrNull()
                    ?: error("The USB device has no serial port")

                candidate.open(connection)
                candidate.setParameters(
                    baudRate,
                    8,
                    UsbSerialPort.STOPBITS_1,
                    UsbSerialPort.PARITY_NONE
                )
                port = candidate
                running.set(true)

                executor.execute {
                    val buffer = ByteArray(4096)
                    try {
                        while (running.get()) {
                            val count = candidate.read(buffer, 500)
                            if (count > 0) {
                                onText(String(buffer, 0, count, Charsets.UTF_8))
                            }
                        }
                    } catch (t: Throwable) {
                        if (running.get()) onClosed(t.message)
                    }
                }
            } catch (t: Throwable) {
                onClosed(t.message)
            }
        }
    }

    fun write(text: String) {
        val current = port ?: return
        executor.execute {
            runCatching {
                current.write(text.toByteArray(Charsets.UTF_8), 1000)
            }.onFailure { onClosed(it.message) }
        }
    }

    fun close() {
        running.set(false)
        runCatching { port?.close() }
        port = null
    }

    fun shutdown() {
        close()
        executor.shutdownNow()
    }
}
