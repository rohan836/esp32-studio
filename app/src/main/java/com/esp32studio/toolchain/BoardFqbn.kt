package com.esp32studio.toolchain

object BoardFqbn {
    fun validate(fqbn: String): String {
        val value = fqbn.trim()
        require(value.count { it == ':' } >= 2) {
            "Invalid FQBN '$fqbn'. Expected vendor:architecture:board."
        }
        require(value.none { it.isWhitespace() }) {
            "FQBN must not contain whitespace."
        }
        return value
    }

    fun coreId(fqbn: String): String {
        val value = validate(fqbn)
        return value.substring(0, value.lastIndexOf(':'))
    }

    fun isEsp32(fqbn: String): Boolean =
        fqbn.startsWith("esp32:esp32:")

    fun isClassicAvr(fqbn: String): Boolean =
        fqbn.startsWith("arduino:avr:")

    fun supportsAndroidNativeFlash(fqbn: String): Boolean =
        isEsp32(fqbn) || isClassicAvr(fqbn)

    fun preferredArtifactExtension(fqbn: String): String =
        if (isClassicAvr(fqbn)) "hex" else "bin"
}
