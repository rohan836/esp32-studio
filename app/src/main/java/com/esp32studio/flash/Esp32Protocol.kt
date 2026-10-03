package com.esp32studio.flash

import java.io.ByteArrayOutputStream

internal object Esp32Protocol {
    fun eraseSize(imageSize: Int): Int {
        require(imageSize > 0) { "Firmware image must not be empty." }
        return ((imageSize + 0xFFF) / 0x1000) * 0x1000
    }

    fun checksum(data: ByteArray): Int {
        var value = 0xEF
        data.forEach { value = value xor (it.toInt() and 0xFF) }
        return value
    }

    fun slipEncode(data: ByteArray): ByteArray {
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
}
