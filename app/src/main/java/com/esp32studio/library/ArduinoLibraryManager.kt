package com.esp32studio.library

import android.content.Context
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

data class ArduinoLibrary(
    val name: String,
    val version: String,
    val author: String,
    val sentence: String,
    val archiveUrl: String,
    val checksum: String
)

class ArduinoLibraryManager(context: Context) {
    private val appContext = context.applicationContext
    private val cacheDir = File(appContext.cacheDir, "arduino-library-index")
    private val librariesDir = File(appContext.filesDir, "arduino/libraries")
    private val indexFile = File(cacheDir, "library_index.json")
    private val indexUrl = "https://downloads.arduino.cc/libraries/library_index.json"

    fun search(query: String, limit: Int = 20): List<ArduinoLibrary> {
        require(query.isNotBlank()) { "Enter a library name to search." }
        require(limit in 1..100) { "Search limit must be between 1 and 100." }
        ensureIndex()
        val needle = query.trim().lowercase(Locale.ROOT)
        val latest = LinkedHashMap<String, ArduinoLibrary>()
        readIndex { library ->
            if (library != null && (
                library.name.lowercase(Locale.ROOT).contains(needle) ||
                library.sentence.lowercase(Locale.ROOT).contains(needle) ||
                library.author.lowercase(Locale.ROOT).contains(needle)
            )) {
                val old = latest[library.name]
                if (old == null || compareVersions(library.version, old.version) > 0) {
                    latest[library.name] = library
                }
            }
        }
        return latest.values.sortedWith(
            compareBy<ArduinoLibrary>({ !it.name.equals(query.trim(), true) }, { it.name.lowercase(Locale.ROOT) })
        ).take(limit)
    }

    fun install(name: String): File {
        require(name.isNotBlank()) { "Enter a library name." }
        var selected: ArduinoLibrary? = null
        readIndex { library ->
            if (library != null && library.name.equals(name.trim(), true) &&
                (selected == null || compareVersions(library.version, selected!!.version) > 0)
            ) selected = library
        }
        val library = selected ?: throw IllegalArgumentException(
            "Library '" + name + "' was not found in the Arduino Library Registry."
        )
        librariesDir.mkdirs()
        val staging = File(librariesDir, ".install-" + System.nanoTime())
        check(staging.mkdirs()) { "Could not create library staging directory." }
        try {
            val archive = File(staging, "library.zip")
            download(library.archiveUrl, archive, 150L * 1024L * 1024L)
            verifyChecksum(archive, library.checksum)
            val extracted = File(staging, "extracted")
            check(extracted.mkdirs()) { "Could not create extraction directory." }
            unzipSafely(archive, extracted)
            val properties = extracted.walkTopDown().maxDepth(5)
                .firstOrNull { it.isFile && it.name == "library.properties" }
                ?: throw IllegalStateException("Archive has no library.properties file.")
            val source = properties.parentFile ?: error("Invalid library archive layout.")
            val folder = sanitizeFolderName(library.name)
            val destination = File(librariesDir, folder).canonicalFile
            check(destination.parentFile == librariesDir.canonicalFile) { "Invalid library destination." }
            val replacement = File(librariesDir, "." + folder + "-new")
            replacement.deleteRecursively()
            check(copyDirectory(source, replacement)) { "Could not stage library files." }
            if (destination.exists()) {
                val backup = File(librariesDir, "." + folder + "-old")
                backup.deleteRecursively()
                check(destination.renameTo(backup)) { "Could not replace installed library." }
                if (!replacement.renameTo(destination)) {
                    backup.renameTo(destination)
                    error("Could not activate new library version.")
                }
                backup.deleteRecursively()
            } else {
                check(replacement.renameTo(destination)) { "Could not activate library." }
            }
            return destination
        } finally {
            staging.deleteRecursively()
        }
    }

    fun installedLibraries(): List<String> =
        librariesDir.listFiles().orEmpty()
            .filter { it.isDirectory && File(it, "library.properties").isFile }
            .map { it.name }
            .sorted()

    private fun readIndex(consume: (ArduinoLibrary?) -> Unit) {
        ensureIndex()
        JsonReader(InputStreamReader(FileInputStream(indexFile), Charsets.UTF_8)).use { reader ->
            reader.beginObject()
            while (reader.hasNext()) {
                if (reader.nextName() == "libraries") {
                    reader.beginArray()
                    while (reader.hasNext()) consume(readLibrary(reader))
                    reader.endArray()
                } else reader.skipValue()
            }
            reader.endObject()
        }
    }

    private fun readLibrary(reader: JsonReader): ArduinoLibrary? {
        var name = ""
        var version = ""
        var author = ""
        var sentence = ""
        var url = ""
        var checksum = ""
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "name" -> name = reader.stringOrEmpty()
                "version" -> version = reader.stringOrEmpty()
                "author" -> author = reader.stringOrEmpty()
                "sentence" -> sentence = reader.stringOrEmpty()
                "url" -> url = reader.stringOrEmpty()
                "checksum" -> checksum = reader.stringOrEmpty()
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        if (name.isBlank() || version.isBlank() || !url.startsWith("https://")) return null
        return ArduinoLibrary(name, version, author, sentence, url, checksum)
    }

    private fun JsonReader.stringOrEmpty(): String = when (peek()) {
        JsonToken.NULL -> { nextNull(); "" }
        JsonToken.STRING, JsonToken.NUMBER -> nextString()
        else -> { skipValue(); "" }
    }

    private fun ensureIndex() {
        cacheDir.mkdirs()
        if (indexFile.isFile &&
            System.currentTimeMillis() - indexFile.lastModified() < TimeUnit.HOURS.toMillis(24)
        ) return
        val temporary = File(cacheDir, "library_index.download")
        download(indexUrl, temporary, 100L * 1024L * 1024L)
        check(temporary.length() > 100L) { "Arduino Library Registry index is empty." }
        if (indexFile.exists()) indexFile.delete()
        check(temporary.renameTo(indexFile)) { "Could not save library index." }
    }

    private fun download(url: String, destination: File, maxBytes: Long) {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15000
        connection.readTimeout = 30000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "ESP32-Studio/0.2")
        try {
            check(connection.responseCode in 200..299) {
                "Download failed (HTTP " + connection.responseCode + ")."
            }
            val size = connection.contentLengthLong
            require(size < 0 || size <= maxBytes) { "Download exceeds size limit." }
            destination.parentFile?.mkdirs()
            connection.inputStream.use { input ->
                FileOutputStream(destination).use { output ->
                    val buffer = ByteArray(32768)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= maxBytes) { "Download exceeds size limit." }
                        output.write(buffer, 0, count)
                    }
                    output.fd.sync()
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun verifyChecksum(file: File, expected: String) {
        val normalized = expected.substringAfter(':', expected).trim().lowercase(Locale.ROOT)
        if (normalized.length != 64 || normalized.any { it !in "0123456789abcdef" }) return
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(32768)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        check(actual == normalized) { "Library archive SHA-256 check failed." }
    }

    private fun unzipSafely(zipFile: File, destination: File) {
        val root = destination.canonicalFile
        ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val output = File(destination, entry.name).canonicalFile
                check(output == root || output.path.startsWith(root.path + File.separator)) {
                    "Unsafe path in library archive."
                }
                if (entry.isDirectory) {
                    check(output.mkdirs() || output.isDirectory) { "Could not create folder." }
                } else {
                    val parent = output.parentFile
                    check(parent != null && (parent.mkdirs() || parent.isDirectory)) { "Could not create folder." }
                    FileOutputStream(output).use { target ->
                        val buffer = ByteArray(16384)
                        var total = 0L
                        while (true) {
                            val count = zip.read(buffer)
                            if (count < 0) break
                            total += count
                            check(total <= 150L * 1024L * 1024L) { "Archive entry exceeds size limit." }
                            target.write(buffer, 0, count)
                        }
                    }
                }
                zip.closeEntry()
            }
        }
    }

    private fun copyDirectory(source: File, destination: File): Boolean {
        if (!destination.mkdirs() && !destination.isDirectory) return false
        for (child in source.listFiles().orEmpty()) {
            val target = File(destination, child.name)
            if (child.isDirectory) {
                if (!copyDirectory(child, target)) return false
            } else {
                child.inputStream().use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
            }
        }
        return true
    }

    private fun sanitizeFolderName(name: String): String =
        name.replace(Regex("[^A-Za-z0-9._ -]"), "_").trim().ifBlank { "ArduinoLibrary" }

    private fun compareVersions(left: String, right: String): Int {
        val a = left.split('.', '-', '+').map { it.toLongOrNull() ?: 0L }
        val b = right.split('.', '-', '+').map { it.toLongOrNull() ?: 0L }
        for (i in 0 until maxOf(a.size, b.size)) {
            val result = a.getOrElse(i) { 0L }.compareTo(b.getOrElse(i) { 0L })
            if (result != 0) return result
        }
        return 0
    }
}
