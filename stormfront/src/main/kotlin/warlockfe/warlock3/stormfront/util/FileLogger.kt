package warlockfe.warlock3.stormfront.util

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okio.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class FileLogger(
    private val directory: Path,
    private val prefix: String,
    ioDispatcher: CoroutineDispatcher,
) {

    private val context = ioDispatcher.limitedParallelism(1)

    init {
        directory.toFile().mkdirs()
    }

    suspend fun write(message: String) {
        withContext(context) {
            val now = LocalDateTime.now()
            val filename = formatFilename(now)
            val logFile = (directory / filename).toFile()
            val dateString = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss,SSS"))
            logFile.appendText("[$dateString] $message\n")
        }
    }

    private fun formatFilename(datetime: LocalDateTime): String {
        return prefix + datetime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + ".log"
    }
}