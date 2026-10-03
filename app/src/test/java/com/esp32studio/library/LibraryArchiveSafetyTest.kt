package com.esp32studio.library

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

class LibraryArchiveSafetyTest {
    @Test
    fun acceptsFileInsideExtractionRoot() {
        val root = File("build/test-library-root")
        val result = LibraryArchiveSafety.outputFile(root, "src/Example.cpp")
        assertEquals(File(root, "src/Example.cpp").canonicalFile, result)
    }

    @Test
    fun rejectsZipSlipTraversal() {
        val root = File("build/test-library-root")
        try {
            LibraryArchiveSafety.outputFile(root, "../../outside.txt")
            fail("Traversal path must be rejected")
        } catch (expected: IllegalArgumentException) {
            assertEquals("Unsafe path in library archive.", expected.message)
        }
    }

    @Test
    fun rejectsAbsolutePathOutsideRoot() {
        val root = File("build/test-library-root")
        val outside = File(root.parentFile, "outside.txt").absolutePath
        try {
            LibraryArchiveSafety.outputFile(root, outside)
            fail("Outside path must be rejected")
        } catch (expected: IllegalArgumentException) {
            assertEquals("Unsafe path in library archive.", expected.message)
        }
    }
}
