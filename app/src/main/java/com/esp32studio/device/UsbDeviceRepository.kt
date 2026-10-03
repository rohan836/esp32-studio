package com.esp32studio.device

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.UsbSerialProber

class UsbDeviceRepository(
    private val usbManager: UsbManager
) {
    fun scan(): List<DeviceInfo> {
        return UsbSerialProber.getDefaultProber()
            .findAllDrivers(usbManager)
            .map { driver ->
                val device = driver.device
                val manufacturer = device.manufacturerName
                val productName = device.productName
                val (family, confidence) = DeviceClassifier.classify(
                    device.vendorId,
                    device.productId,
                    manufacturer,
                    productName
                )
                DeviceInfo(
                    key = device.vendorId.toString() + ":" + device.productId + ":" + device.deviceId,
                    vendorId = device.vendorId,
                    productId = device.productId,
                    manufacturer = manufacturer,
                    productName = productName,
                    boardFamily = family,
                    confidence = confidence,
                    portCount = driver.ports.size
                )
            }
            .sortedBy { it.confidence.ordinal }
    }

    fun findDriver(device: UsbDevice) =
        UsbSerialProber.getDefaultProber().probeDevice(device)
}
