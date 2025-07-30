package warlockfe.warlock3.core.util

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.io.files.FileNotFoundException
import kotlinx.io.files.Path
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class FileLogger private constructor(
    private val outputStream: FileOutputStream,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1),
) : AutoCloseable {

    private val writer: BufferedWriter = outputStream.bufferedWriter()

    suspend fun write(message: String, addTimestamps: Boolean, logType: LogType) {
        withContext(dispatcher) {
            if (addTimestamps) {
                val now = LocalDateTime.now()
                val dateString = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss,SSS"))
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
        runBlocking(dispatcher) {
            writer.close()
            outputStream.close()
        }
    }

    companion object {
        private val logger = KotlinLogging.logger {}

        fun getLogger(
            directory: Path,
            timestamp: LocalDateTime = LocalDateTime.now(),
        ): FileLogger? {
            val formattedDate = timestamp.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
            val path = Path(directory, "$formattedDate.log")
            val file = File(path.toString())
            file.parentFile?.mkdirs()
            try {
                return FileLogger(
                    outputStream = FileOutputStream(file, true)
                )
            } catch (_: FileNotFoundException) {
                logger.warn { "Failed to create file logger for $path" }
                return null
            }
        }
    }
}
