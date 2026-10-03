package com.esp32studio.flash

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.ceil

data class EspProbeResult(
    val bootloaderResponding: Boolean,
    val usbDevice: String
)

class Esp32Bootloader(
    private val usbManager: UsbManager,
    private val onProgress: (sent: Int, total: Int) -> Unit = { _, _ -> }
) {
    private companion object {
        const val SYNC = 0x08
        const val FLASH_BEGIN = 0x02
        const val FLASH_DATA = 0x03
        const val FLASH_END = 0x04
        const val PACKET_SIZE = 0x4000
        const val SECTOR_SIZE = 0x1000
        const val RESPONSE_TIMEOUT_MS = 2000L
    }

    fun probe(device: UsbDevice): EspProbeResult {
        return withPort(device) { port ->
            enterBootloader(port)
            sync(port)
            EspProbeResult(true, device.deviceName)
        }
    }

    fun flash(device: UsbDevice, image: File, address: Long) {
        require(image.exists() && image.isFile) {
            "Firmware image does not exist: ${image.absolutePath}"
        }
        require(address >= 0 && address <= 0xFFFFFFFFL) {
            "Flash address is outside ESP32 address space."
        }

        val bytes = image.readBytes()
        require(bytes.isNotEmpty()) { "Firmware image is empty." }

        withPort(device) { port ->
            enterBootloader(port)
            sync(port)
            flashImage(port, bytes, address)
        }
    }

    private fun <T> withPort(device: UsbDevice, block: (UsbSerialPort) -> T): T {
        val driver = UsbSerialProber.getDefaultProber().probeDevice(device)
            ?: error("No USB serial driver found for ${device.deviceName}")

        check(usbManager.hasPermission(device)) {
            "Android USB permission is not granted."
        }

        val connection = usbManager.openDevice(device)
            ?: error("Android could not open ${device.deviceName}")

        val port = driver.ports.firstOrNull()
            ?: error("The USB device has no serial port")

        try {
            port.open(connection)
            port.setParameters(
                115200,
                8,
                UsbSerialPort.STOPBITS_1,
                UsbSerialPort.PARITY_NONE
            )
            port.setDTR(false)
            port.setRTS(false)
            return block(port)
        } finally {
            runCatching { port.setDTR(false) }
            runCatching { port.setRTS(false) }
            runCatching { port.close() }
        }
    }

    private fun enterBootloader(port: UsbSerialPort) {
        // DTR is normally wired to GPIO0 and RTS to EN on ESP32 development boards.
        // Both modem signals are active-low.
        port.setDTR(false)
        port.setRTS(true)
        TimeUnit.MILLISECONDS.sleep(100)
        port.setDTR(true)
        port.setRTS(false)
        TimeUnit.MILLISECONDS.sleep(100)
        port.setDTR(false)
    }

    private fun sync(port: UsbSerialPort) {
        val payload = ByteArray(36).apply {
            this[0] = 0x07
            this[1] = 0x07
            this[2] = 0x12
            this[3] = 0x20
            for (i in 4 until size) this[i] = 0x55
        }

        repeat(8) {
            val response = sendCommand(port, SYNC, payload)
            if (response.command == SYNC) return
        }

        error("ESP32 UART bootloader did not respond. Put the board in download mode and try again.")
    }

    private fun flashImage(port: UsbSerialPort, image: ByteArray, address: Long) {
        val eraseSize = ((image.size + SECTOR_SIZE - 1) / SECTOR_SIZE) * SECTOR_SIZE
        val blockCount = ceil(eraseSize / PACKET_SIZE.toDouble()).toInt()

        val begin = ByteArrayOutputStream().apply {
            writeLe32(eraseSize.toLong())
            writeLe32(blockCount.toLong())
            writeLe32(PACKET_SIZE.toLong())
            writeLe32(address)
        }.toByteArray()

        expectSuccess(sendCommand(port, FLASH_BEGIN, begin))

        for (sequence in 0 until blockCount) {
            val start = sequence * PACKET_SIZE
            val end = minOf(start + PACKET_SIZE, image.size)
            val chunk = ByteArray(PACKET_SIZE) { index ->
                val sourceIndex = start + index
                if (sourceIndex < end) image[sourceIndex] else 0xFF.toByte()
            }

            val dataSize = (image.size - start).coerceIn(0, PACKET_SIZE)
            val data = ByteArrayOutputStream().apply {
                writeLe32(dataSize.toLong())
                writeLe32(sequence.toLong())
                writeLe32(0)
                writeLe32(0)
                write(chunk)
            }.toByteArray()

            expectSuccess(sendCommand(port, FLASH_DATA, data, checksum(chunk)))
            onProgress(end.coerceAtMost(image.size), image.size)
        }

        expectSuccess(sendCommand(port, FLASH_END, ByteArray(4)))
    }

    private fun sendCommand(
        port: UsbSerialPort,
        command: Int,
        data: ByteArray,
        checksum: Int = 0
    ): Packet {
        val body = ByteArrayOutputStream().apply {
            write(0)
            write(command)
            writeLe16(data.size)
            writeLe32(checksum.toLong())
            write(data)
        }.toByteArray()

        port.write(slipEncode(body), 3000)
        return readPacket(port, RESPONSE_TIMEOUT_MS, expectedCommand = command)
    }

    private fun expectSuccess(packet: Packet) {
        if (packet.data.size >= 2) {
            val status = packet.data[packet.data.size - 2].toInt() and 0xFF
            if (status != 0) {
                val error = packet.data.last().toInt() and 0xFF
                throw IllegalStateException(
                    "ESP32 bootloader command failed: status=0x%02X error=0x%02X"
                        .format(status, error)
                )
            }
        }
    }

    private fun checksum(data: ByteArray): Int {
        var value = 0xEF
        data.forEach { value = value xor (it.toInt() and 0xFF) }
        return value
    }

    private fun readPacket(
        port: UsbSerialPort,
        timeoutMs: Long,
        expectedCommand: Int? = null
    ): Packet {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        var inFrame = false
        var escaped = false
        val buffer = ByteArrayOutputStream()

        while (System.nanoTime() < deadline) {
            val remainingMs = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime())
                .coerceIn(1, 500)
            val chunk = ByteArray(1024)
            val count = port.read(chunk, remainingMs.toInt())

            for (i in 0 until count) {
                val byte = chunk[i].toInt() and 0xFF
                when {
                    byte == 0xC0 && !inFrame -> {
                        inFrame = true
                        escaped = false
                        buffer.reset()
                    }
                    byte == 0xC0 && inFrame -> {
                        if (buffer.size() == 0) continue
                        val packet = parsePacket(buffer.toByteArray())
                        if (expectedCommand == null || packet.command == expectedCommand) {
                            return packet
                        }
                        buffer.reset()
                        escaped = false
                    }
                    inFrame && escaped -> {
                        buffer.write(
                            when (byte) {
                                0xDC -> 0xC0
                                0xDD -> 0xDB
                                else -> byte
                            }
                        )
                        escaped = false
                    }
                    inFrame && byte == 0xDB -> escaped = true
                    inFrame -> buffer.write(byte)
                }
            }
        }

        error("Timed out waiting for ESP32 bootloader response.")
    }

    private fun parsePacket(bytes: ByteArray): Packet {
        require(bytes.size >= 8) { "Invalid ESP32 bootloader packet." }
        val command = bytes[1].toInt() and 0xFF
        val size = (bytes[2].toInt() and 0xFF) or
            ((bytes[3].toInt() and 0xFF) shl 8)
        require(bytes.size >= 8 + size) { "Truncated ESP32 bootloader packet." }

        return Packet(
            command = command,
            value = readLe32(bytes, 4),
            data = bytes.copyOfRange(8, 8 + size)
        )
    }

    private fun slipEncode(data: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        output.write(0xC0)
        data.forEach { value ->
            when (value.toInt() and 0xFF) {
                0xC0 -> {
                    output.write(0xDB)
                    output.write(0xDC)
                }
                0xDB -> {
                    output.write(0xDB)
                    output.write(0xDD)
                }
                else -> output.write(value.toInt() and 0xFF)
            }
        }
        output.write(0xC0)
        return output.toByteArray()
    }

    private fun ByteArrayOutputStream.writeLe16(value: Int) {
        write(value and 0xFF)
        write((value ushr 8) and 0xFF)
    }

    private fun ByteArrayOutputStream.writeLe32(value: Long) {
        write((value and 0xFF).toInt())
        write(((value ushr 8) and 0xFF).toInt())
        write(((value ushr 16) and 0xFF).toInt())
        write(((value ushr 24) and 0xFF).toInt())
    }

    private fun readLe32(bytes: ByteArray, offset: Int): Long {
        return (bytes[offset].toLong() and 0xFF) or
            ((bytes[offset + 1].toLong() and 0xFF) shl 8) or
            ((bytes[offset + 2].toLong() and 0xFF) shl 16) or
            ((bytes[offset + 3].toLong() and 0xFF) shl 24)
    }

    private data class Packet(
        val command: Int,
        val value: Long,
        val data: ByteArray
    )
}
