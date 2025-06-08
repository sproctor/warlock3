package warlockfe.warlock3.core.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Path
import java.io.BufferedWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class FileLogger(
    private val directory: Path,
): AutoCloseable {

    private val context = Dispatchers.IO.limitedParallelism(1)

    private var cachedStream: BufferedWriter? = null
    private var dateString: String? = null

    suspend fun write(message: String, addTimestamps: Boolean) {
        withContext(context) {
            val now = LocalDateTime.now()
            val stream = getStream(now)
            if (addTimestamps) {
                val dateString = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss,SSS"))
                stream.write("[$dateString] $message\n")
            } else {
                stream.write("$message\n")
            }
            stream.flush()
        }
    }

    private fun getStream(now: LocalDateTime): BufferedWriter {
        val formattedDate = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        if (cachedStream == null || dateString != formattedDate) {
            directory.toFile().mkdirs()
            val file = directory / "$formattedDate.log"
            file.parent?.toFile()?.mkdirs()
            cachedStream?.close()
            cachedStream = file.toFile().outputStream().bufferedWriter()
            dateString = formattedDate
        }
        return cachedStream!!
    }

    override fun close() {
        cachedStream?.close()
        cachedStream = null
        dateString = null
    }
}
