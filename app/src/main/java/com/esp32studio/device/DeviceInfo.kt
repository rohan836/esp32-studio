package com.esp32studio.device

data class DeviceInfo(
    val key: String,
    val vendorId: Int,
    val productId: Int,
    val manufacturer: String?,
    val productName: String?,
    val boardFamily: String?,
    val confidence: Confidence,
    val portCount: Int
) {
    enum class Confidence { HIGH, MEDIUM, LOW }
}
