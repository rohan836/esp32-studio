package com.esp32studio.library

import java.io.File

internal object LibraryArchiveSafety {
    fun outputFile(root: File, entryName: String): File {
        require(entryName.isNotBlank()) { "Archive entry name is empty." }
        val canonicalRoot = root.canonicalFile
        val output = File(root, entryName).canonicalFile
        require(output == canonicalRoot || output.path.startsWith(canonicalRoot.path + File.separator)) {
            "Unsafe path in library archive."
        }
        return output
    }
}
