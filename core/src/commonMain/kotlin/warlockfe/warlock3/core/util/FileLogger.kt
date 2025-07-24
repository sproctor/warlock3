package warlockfe.warlock3.core.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okio.Path
import java.io.BufferedWriter
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class FileLogger(
    directory: Path,
    timestamp: LocalDateTime = LocalDateTime.now(),
): AutoCloseable {

    private val context = Dispatchers.IO.limitedParallelism(1)

    private val outputStream: FileOutputStream
    private val writer: BufferedWriter

    init {
        val formattedDate = timestamp.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
        val path = directory / "$formattedDate.log"
        path.parent?.toFile()?.mkdirs()
        outputStream = FileOutputStream(path.toFile(), true)
        writer = outputStream.bufferedWriter()
    }

    suspend fun write(message: String, addTimestamps: Boolean, logType: LogType) {
        withContext(context) {
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
        runBlocking(context) {
            writer.close()
            outputStream.close()
        }
    }
}
