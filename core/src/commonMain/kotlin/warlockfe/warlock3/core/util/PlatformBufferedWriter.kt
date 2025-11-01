package warlockfe.warlock3.core.util

import kotlinx.io.files.Path

expect class PlatformBufferedWriter {
    fun write(message: String)

    fun flush()

    fun close()
}

expect fun createPlatformBufferedWriter(path: Path): PlatformBufferedWriter