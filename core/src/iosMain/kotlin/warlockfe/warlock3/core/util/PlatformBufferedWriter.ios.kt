package warlockfe.warlock3.core.util

import kotlinx.io.IOException
import kotlinx.io.files.Path

actual class PlatformBufferedWriter(path: Path) {


    init {
//        try {
//            val file = File(path.toString())
//            file.parentFile?.mkdirs()
//            writer = file.bufferedWriter()
//        } catch (_: IOException) {
//            // ignore exceptions
//        }
    }

    actual fun write(message: String) {
//        writer?.write(message)
    }

    actual fun flush() {
//        writer?.flush()
    }

    actual fun close() {
//        writer?.close()
    }
}

actual fun createPlatformBufferedWriter(path: Path): PlatformBufferedWriter {
    return PlatformBufferedWriter(path)
}
