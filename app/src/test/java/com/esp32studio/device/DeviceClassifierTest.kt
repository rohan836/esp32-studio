package com.esp32studio.device

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceClassifierTest {
    @Test
    fun espressifVendorIsHighConfidence() {
        val result = DeviceClassifier.classify(
            0x303A,
            0x1001,
            "Espressif",
            "USB JTAG/serial"
        )
        assertEquals(DeviceInfo.Confidence.HIGH, result.second)
        assertEquals("ESP32", result.first)
    }

    @Test
    fun ch340IsMediumConfidence() {
        val result = DeviceClassifier.classify(
            0x1A86,
            0x7523,
            "QinHeng Electronics",
            "USB-SERIAL CH340"
        )
        assertEquals(DeviceInfo.Confidence.MEDIUM, result.second)
    }

    @Test
    fun unknownDeviceDoesNotGetGuessedAsEsp32() {
        val result = DeviceClassifier.classify(
            0x9999,
            0x0001,
            "Unknown",
            "Widget"
        )
        assertEquals(DeviceInfo.Confidence.LOW, result.second)
        assertEquals(null, result.first)
    }
}
