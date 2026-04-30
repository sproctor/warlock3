package warlockfe.warlock3.core.util

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CloseableCoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
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
) {

    private val channel = Channel<String>(Channel.UNLIMITED)

    private val job: Job = CoroutineScope(dispatcher).launch {
        try {
            for (message in channel) {
                writer.write(message)
                if (channel.isEmpty) {
                    writer.flush()
                }
            }
            writer.flush()
        } catch (e: IOException) {
            logger.e(e) { "Error monitoring log channel" }
            channel.close(e)
        }
    }

    suspend fun write(message: String, addTimestamps: Boolean, logType: LogType) {
        try {
            channel.send(
                if (addTimestamps) {
                    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                    val dateString = now.toString()
                    if (logType == LogType.COMPLETE) {
                        "<timestamp time=\"$dateString\"/>$message\n"
                    } else {
                        "[$dateString] $message\n"
                    }
                } else {
                    "$message\n"
                }
            )
        } catch (_: ClosedSendChannelException) {
            // Logger was closed; drop the message
        } catch (e: IOException) {
            logger.e(e) { "Error while logging: $message" }
        }
    }

    suspend fun close() {
        channel.close()
        job.join()
        try {
            writer.close()
        } catch (e: IOException) {
            logger.e(e) { "Error closing log writer" }
        }
        dispatcher.close()
    }

    companion object {
        private val logger = Logger.withTag("FileLogger")

        fun getLogger(
            directory: Path,
            timestamp: LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
        ): FileLogger? {
            val formattedDate = fileDateFormat.format(timestamp)
            val path = Path(directory, "$formattedDate.log")
            try {
                return FileLogger(createPlatformBufferedWriter(path))
            } catch (e: IOException) {
                logger.w(e) { "Failed to create file logger for $path" }
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