package warlockfe.warlock3.core.prefs.config

import co.touchlab.kermit.Logger
import kotlinx.io.files.Path
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileLock

internal actual fun withFileLock(
    lockFile: Path,
    block: () -> Unit,
) {
    val file = File(lockFile.toString())
    file.parentFile?.mkdirs()
    val handle =
        try {
            RandomAccessFile(file, "rw")
        } catch (e: Exception) {
            Logger.w(e) { "Could not open lock file $lockFile; writing without a cross-process lock" }
            block()
            return
        }
    handle.use { raf ->
        var lock: FileLock? = null
        try {
            // Exclusive, blocks until any other process holding the lock releases it.
            lock = raf.channel.lock()
        } catch (e: Exception) {
            Logger.w(e) { "Could not acquire lock on $lockFile; writing without a cross-process lock" }
        }
        try {
            block()
        } finally {
            try {
                lock?.release()
            } catch (_: Exception) {
                // Closing the channel via use{} releases the lock anyway.
            }
        }
    }
}
