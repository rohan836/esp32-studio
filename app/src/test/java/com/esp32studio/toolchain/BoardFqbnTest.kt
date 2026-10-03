package com.esp32studio.toolchain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardFqbnTest {
    @Test
    fun extractsCoreIdFromFqbn() {
        assertEquals("arduino:avr", BoardFqbn.coreId("arduino:avr:uno"))
        assertEquals("esp32:esp32", BoardFqbn.coreId("esp32:esp32:esp32"))
    }

    @Test
    fun recognizesNativeAndroidFlashTargets() {
        assertTrue(BoardFqbn.supportsAndroidNativeFlash("esp32:esp32:esp32"))
        assertTrue(BoardFqbn.supportsAndroidNativeFlash("arduino:avr:uno"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidFqbn() {
        BoardFqbn.validate("uno")
    }
}
