package warlockfe.warlock3.core.util

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CloseableCoroutineDispatcher
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.io.IOException
import kotlinx.io.files.Path
import kotlin.time.Clock

@OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
class FileLogger private constructor(
    private val writer: PlatformBufferedWriter,
    private val dispatcher: CloseableCoroutineDispatcher = newSingleThreadContext("FileLogger"),
) : AutoCloseable {

    suspend fun write(message: String, addTimestamps: Boolean, logType: LogType) {
        withContext(dispatcher) {
            try {
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
            } catch (e: IOException) {
                logger.error(e) { "Error while logging: $message" }
            }
        }
    }

    override fun close() {
        dispatcher.close()
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
            try {
                return FileLogger(createPlatformBufferedWriter(path))
            } catch (e: IOException) {
                logger.warn(e) { "Failed to create file logger for $path" }
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