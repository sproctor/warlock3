package warlockfe.warlock3.stormfront.util

import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class FileLogger(
    private val path: String,
    private val id: String
) {

    private var cachedFilename = ""
    private lateinit var logFile: File

    init {
        File("$path/$id").mkdirs()
    }

    @Synchronized
    fun write(message: String) {
        val now = LocalDateTime.now()
        val filename = formatFilename(now)
        val logFile = File("$path/$id/$filename")
        val dateString = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss,SSS"))
        logFile.appendText("[$dateString] $message\n")
    }

    private fun formatFilename(datetime: LocalDateTime): String {
        return datetime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + ".log"
    }
}