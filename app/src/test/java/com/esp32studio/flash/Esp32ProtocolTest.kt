package com.esp32studio.flash

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class Esp32ProtocolTest {
    @Test
    fun roundsFlashEraseSizeToSectorBoundary() {
        assertEquals(0x1000, Esp32Protocol.eraseSize(1))
        assertEquals(0x1000, Esp32Protocol.eraseSize(0x1000))
        assertEquals(0x2000, Esp32Protocol.eraseSize(0x1001))
    }

    @Test
    fun calculatesEspChecksum() {
        assertEquals(
            0xEF xor 0x01 xor 0x02 xor 0x03,
            Esp32Protocol.checksum(byteArrayOf(1, 2, 3))
        )
    }

    @Test
    fun wrapsSlipPacket() {
        assertArrayEquals(
            byteArrayOf(
                0xC0.toByte(),
                0x01,
                0xDB.toByte(),
                0xDC.toByte(),
                0xDB.toByte(),
                0xDD.toByte(),
                0xC0.toByte()
            ),
            Esp32Protocol.slipEncode(
                byteArrayOf(0x01, 0xC0.toByte(), 0xDB.toByte())
            )
        )
    }

    @Test
    fun leavesOrdinaryBytesUnchanged() {
        assertArrayEquals(
            byteArrayOf(0xC0.toByte(), 1, 2, 3, 0xC0.toByte()),
            Esp32Protocol.slipEncode(byteArrayOf(1, 2, 3))
        )
    }
}
