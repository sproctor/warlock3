package warlockfe.warlock3.stormfront.util

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.Path
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class FileLogger(
    private val directory: Path,
) {

    private val mutex = Mutex()

    init {
        directory.toFile().mkdirs()
    }

    suspend fun write(message: String) {
        val now = LocalDateTime.now()
        val filename = formatFilename(now)
        val logFile = (directory / filename).toFile()
        val dateString = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss,SSS"))
        mutex.withLock {
            logFile.appendText("[$dateString] $message\n")
        }
    }

    private fun formatFilename(datetime: LocalDateTime): String {
        return datetime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + ".log"
    }
}