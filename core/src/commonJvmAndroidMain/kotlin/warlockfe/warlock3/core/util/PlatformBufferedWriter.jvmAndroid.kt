package warlockfe.warlock3.core.util

import kotlinx.io.files.Path
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream

actual class PlatformBufferedWriter(path: Path) {

    var writer: BufferedWriter? = null

    init {
        val file = File(path.toString())
        file.parentFile?.mkdirs()
        writer = FileOutputStream(file, true).bufferedWriter()
    }

    actual fun write(message: String) {
        writer?.write(message)
    }

    actual fun flush() {
        writer?.flush()
    }

    actual fun close() {
        writer?.close()
    }
}

actual fun createPlatformBufferedWriter(path: Path): PlatformBufferedWriter {
    return PlatformBufferedWriter(path)
}