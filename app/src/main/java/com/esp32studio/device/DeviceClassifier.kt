package com.esp32studio.device

object DeviceClassifier {
    fun classify(
        vendorId: Int,
        productId: Int,
        manufacturer: String?,
        productName: String?
    ): Pair<String?, DeviceInfo.Confidence> {
        val text = listOfNotNull(manufacturer, productName)
            .joinToString(" ")
            .lowercase()

        return when {
            vendorId == 0x303A || text.contains("espressif") ->
                "ESP32" to DeviceInfo.Confidence.HIGH
            text.contains("esp32") ->
                "ESP32" to DeviceInfo.Confidence.HIGH
            vendorId == 0x10C4 || text.contains("silicon labs") || text.contains("cp210") ->
                "ESP32-capable USB serial" to DeviceInfo.Confidence.MEDIUM
            vendorId == 0x1A86 || text.contains("ch340") || text.contains("ch341") ->
                "ESP32-capable USB serial" to DeviceInfo.Confidence.MEDIUM
            vendorId == 0x0403 || text.contains("ftdi") ->
                "ESP32-capable USB serial" to DeviceInfo.Confidence.MEDIUM
            vendorId == 0x067B || text.contains("prolific") ->
                "ESP32-capable USB serial" to DeviceInfo.Confidence.MEDIUM
            else ->
                null to DeviceInfo.Confidence.LOW
        }
    }
}
