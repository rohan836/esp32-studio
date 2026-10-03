package com.esp32studio.flash

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import java.io.File
import java.util.concurrent.TimeUnit

class AvrStk500Uploader(
    private val usbManager: UsbManager,
    private val onProgress: (sent: Int, total: Int) -> Unit = { _, _ -> }
) {
    private companion object {
        const val STK_OK = 0x10
        const val STK_INSYNC = 0x14
        const val CRC_EOP = 0x20
        const val STK_GET_SYNC = 0x30
        const val STK_GET_SIGN_ON = 0x31
        const val STK_ENTER_PROGMODE_ISP = 0x50
        const val STK_LEAVE_PROGMODE_ISP = 0x51
        const val STK_LOAD_ADDRESS = 0x55
        const val STK_PROG_PAGE = 0x64
        const val STK_READ_SIGN = 0x75
        const val PAGE_SIZE = 128
        const val RESPONSE_TIMEOUT_MS = 1500L
    }

    fun flashUno(device: UsbDevice, hexFile: File) {
        require(hexFile.isFile) { "AVR HEX image does not exist: " + hexFile.absolutePath }
        val image = IntelHex.parse(hexFile)

        withPort(device) { port ->
            resetIntoBootloader(port)
            sync(port)
            signOn(port)
            enterProgramming(port)

            try {
                var address = 0
                while (address < image.data.size) {
                    val count = minOf(PAGE_SIZE, image.data.size - address)
                    val page = image.data.copyOfRange(address, address + count)
                    if (page.any { (it.toInt() and 0xFF) != 0xFF }) {
                        loadAddress(port, address / 2)
                        programPage(port, page)
                    }
                    address += count
                    onProgress(address.coerceAtMost(image.data.size), image.data.size)
                }
            } finally {
                leaveProgramming(port)
                resetIntoApplication(port)
            }
        }
    }

    private fun <T> withPort(device: UsbDevice, block: (UsbSerialPort) -> T): T {
        val driver = UsbSerialProber.getDefaultProber().probeDevice(device)
            ?: error("No USB serial driver found for " + device.deviceName)
        check(usbManager.hasPermission(device)) { "Android USB permission is not granted." }
        val connection = usbManager.openDevice(device)
            ?: error("Android could not open " + device.deviceName)
        val port = driver.ports.firstOrNull()
            ?: error("The USB device has no serial port")
        try {
            port.open(connection)
            port.setParameters(
                115200, 8,
                UsbSerialPort.STOPBITS_1,
                UsbSerialPort.PARITY_NONE
            )
            return block(port)
        } finally {
            runCatching { port.setDTR(false) }
            runCatching { port.setRTS(false) }
            runCatching { port.close() }
        }
    }

    private fun resetIntoBootloader(port: UsbSerialPort) {
        port.setDTR(false)
        port.setRTS(false)
        drain(port)
        TimeUnit.MILLISECONDS.sleep(50)
        port.setDTR(true)
        TimeUnit.MILLISECONDS.sleep(100)
        port.setDTR(false)
        TimeUnit.MILLISECONDS.sleep(500)
        drain(port)
    }

    private fun resetIntoApplication(port: UsbSerialPort) {
        port.setDTR(true)
        TimeUnit.MILLISECONDS.sleep(50)
        port.setDTR(false)
    }

    private fun drain(port: UsbSerialPort) {
        val buffer = ByteArray(256)
        repeat(4) { runCatching { port.read(buffer, 25) } }
    }

    private fun sync(port: UsbSerialPort) {
        repeat(8) {
            write(port, STK_GET_SYNC)
            if (readStatus(port)) return
            TimeUnit.MILLISECONDS.sleep(25)
        }
        error("AVR bootloader did not enter STK500v1 mode. Check the board and port.")
    }

    private fun signOn(port: UsbSerialPort) {
        write(port, STK_GET_SIGN_ON)
        check(readResponse(port).size >= 3) { "AVR bootloader sign-on failed." }
    }

    private fun enterProgramming(port: UsbSerialPort) {
        write(port, STK_ENTER_PROGMODE_ISP)
        require(readStatus(port)) { "AVR bootloader refused programming mode." }

        write(port, STK_READ_SIGN)
        val response = readResponse(port)
        require(
            response.size >= 5 &&
                (response[0].toInt() and 0xFF) == STK_INSYNC &&
                (response[4].toInt() and 0xFF) == STK_OK
        ) { "AVR device signature read failed." }
    }

    private fun leaveProgramming(port: UsbSerialPort) {
        runCatching {
            write(port, STK_LEAVE_PROGMODE_ISP)
            readStatus(port)
        }
    }

    private fun loadAddress(port: UsbSerialPort, wordAddress: Int) {
        val bytes = byteArrayOf(
            STK_LOAD_ADDRESS.toByte(),
            (wordAddress and 0xFF).toByte(),
            ((wordAddress ushr 8) and 0xFF).toByte(),
            CRC_EOP.toByte()
        )
        port.write(bytes, 1000)
        require(readStatus(port)) { "AVR bootloader rejected flash address." }
    }

    private fun programPage(port: UsbSerialPort, page: ByteArray) {
        require(page.isNotEmpty() && page.size <= PAGE_SIZE)
        val command = ByteArray(4 + page.size)
        command[0] = STK_PROG_PAGE.toByte()
        command[1] = ((page.size ushr 8) and 0xFF).toByte()
        command[2] = (page.size and 0xFF).toByte()
        command[3] = 'F'.code.toByte()
        page.copyInto(command, 4)
        port.write(command + byteArrayOf(CRC_EOP.toByte()), 3000)
        require(readStatus(port)) { "AVR bootloader rejected a flash page." }
    }

    private fun write(port: UsbSerialPort, command: Int) {
        port.write(byteArrayOf(command.toByte(), CRC_EOP.toByte()), 1000)
    }

    private fun readStatus(port: UsbSerialPort): Boolean {
        val response = readResponse(port)
        return response.size >= 2 &&
            (response[0].toInt() and 0xFF) == STK_INSYNC &&
            (response[1].toInt() and 0xFF) == STK_OK
    }

    private fun readResponse(port: UsbSerialPort): ByteArray {
        val buffer = ByteArray(512)
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(RESPONSE_TIMEOUT_MS)
        val output = ArrayList<Byte>()
        while (System.nanoTime() < deadline) {
            val remaining = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime())
                .coerceIn(1, 250)
            val count = port.read(buffer, remaining.toInt())
            for (i in 0 until count) output += buffer[i]
            if (output.size >= 2 && (output[0].toInt() and 0xFF) == STK_INSYNC) {
                val last = output.last().toInt() and 0xFF
                if (last == STK_OK) return output.toByteArray()
            }
        }
        error("Timed out waiting for AVR bootloader response.")
    }

    private object IntelHex {
        data class Image(val data: ByteArray)

        fun parse(file: File): Image {
            var linearUpper = 0
            var segmentBase = 0
            var maxAddress = -1
            val records = mutableListOf<Pair<Int, ByteArray>>()

            file.forEachLine { raw ->
                val line = raw.trim()
                if (line.isEmpty()) return@forEachLine
                require(line.startsWith(":")) { "Invalid Intel HEX record." }
                require((line.length - 1) % 2 == 0) { "Invalid Intel HEX length." }

                val bytes = ByteArray((line.length - 1) / 2) { index ->
                    line.substring(1 + index * 2, 3 + index * 2).toInt(16).toByte()
                }
                val count = bytes[0].toInt() and 0xFF
                val address = ((bytes[1].toInt() and 0xFF) shl 8) or
                    (bytes[2].toInt() and 0xFF)
                val type = bytes[3].toInt() and 0xFF
                require(count == bytes.size - 5) { "Intel HEX byte count mismatch." }

                var checksum = 0
                bytes.forEach { checksum = (checksum + (it.toInt() and 0xFF)) and 0xFF }
                require(checksum == 0) { "Intel HEX checksum mismatch." }

                when (type) {
                    0x00 -> {
                        val absolute = if (linearUpper != 0) {
                            (linearUpper shl 16) + address
                        } else {
                            (segmentBase shl 4) + address
                        }
                        val payload = bytes.copyOfRange(4, 4 + count)
                        records += absolute to payload
                        maxAddress = maxOf(maxAddress, absolute + count - 1)
                    }
                    0x01 -> Unit
                    0x02 -> {
                        require(count == 2) { "Invalid extended segment address record." }
                        segmentBase = ((bytes[4].toInt() and 0xFF) shl 8) or
                            (bytes[5].toInt() and 0xFF)
                        linearUpper = 0
                    }
                    0x04 -> {
                        require(count == 2) { "Invalid extended linear address record." }
                        linearUpper = ((bytes[4].toInt() and 0xFF) shl 8) or
                            (bytes[5].toInt() and 0xFF)
                        segmentBase = 0
                    }
                    0x05 -> Unit
                    else -> Unit
                }
            }

            require(maxAddress >= 0) { "Intel HEX file contains no program data." }
            val image = ByteArray(maxAddress + 1) { 0xFF.toByte() }
            records.forEach { (address, payload) -> payload.copyInto(image, address) }
            return Image(image)
        }
    }
}
