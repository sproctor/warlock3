package warlockfe.warlock3.core.util

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.io.files.FileNotFoundException
import kotlinx.io.files.Path
import kotlin.time.Clock

class FileLogger private constructor(
    private val writer: PlatformBufferedWriter,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1),
) : AutoCloseable {

    suspend fun write(message: String, addTimestamps: Boolean, logType: LogType) {
        withContext(dispatcher) {
            if (addTimestamps) {
                val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                val dateString = now.toString()
                if (logType == LogType.COMPLETE) {
                    writer.write("<timestamp time=\"$dateString\"/>$message\n")
                } else {
                    writer.write("[$dateString] $message\n")
                }
            } else {
                writer.write("$message\n")
            }
            writer.flush()
        }
    }

    override fun close() {
        writer.close()
    }

    companion object {
        private val logger = KotlinLogging.logger {}

        fun getLogger(
            directory: Path,
            timestamp: LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
        ): FileLogger? {
            val formattedDate = fileDateFormat.format(timestamp)
            val path = Path(directory, "$formattedDate.log")
//            val file = File(path.toString())
//            file.parentFile?.mkdirs()
            try {
                return FileLogger(
                    createPlatformBufferedWriter(path)
//                    outputStream = FileOutputStream(file, true)
                )
            } catch (_: FileNotFoundException) {
                logger.warn { "Failed to create file logger for $path" }
                return null
            }
        }
    }
}

private val fileDateFormat = LocalDateTime.Format {
    year()
    monthNumber()
    day()
    hour()
    minute()
    second()
}