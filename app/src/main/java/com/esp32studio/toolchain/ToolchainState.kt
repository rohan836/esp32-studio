package com.esp32studio.toolchain

enum class ToolchainState {
    NOT_INSTALLED,
    READY,
    FAILED
}

data class ToolchainStatus(
    val state: ToolchainState,
    val detail: String
)
