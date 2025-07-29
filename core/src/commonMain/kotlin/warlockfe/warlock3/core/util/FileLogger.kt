package warlockfe.warlock3.core.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.io.files.Path
import java.io.File

class FileLogger(
    directory: Path,
    timestamp: LocalDateTime = LocalDateTime.now(),
): AutoCloseable {

    private val context = Dispatchers.IO.limitedParallelism(1)

    private val outputStream: FileOutputStream
    private val writer: BufferedWriter

    init {
        val formattedDate = timestamp.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
        val path = Path(directory, "$formattedDate.log")
        val file = File(path.toString())
        file.parentFile?.mkdirs()
        outputStream = FileOutputStream(file, true)
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
