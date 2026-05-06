package warlockfe.warlock3.core.util

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.io.files.Path
import platform.Foundation.NSFileHandle
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.closeFile
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Foundation.fileHandleForWritingAtPath
import platform.Foundation.synchronizeFile
import platform.Foundation.writeData

@OptIn(ExperimentalForeignApi::class)
actual class PlatformBufferedWriter(
    path: Path,
) {
    private var fileHandle: NSFileHandle? = null

    init {
        try {
            val pathStr = path.toString()
            val parent = pathStr.substringBeforeLast('/')
            NSFileManager.defaultManager.createDirectoryAtPath(parent, true, null, null)
            if (!NSFileManager.defaultManager.fileExistsAtPath(pathStr)) {
                NSFileManager.defaultManager.createFileAtPath(pathStr, null, null)
            }
            fileHandle = NSFileHandle.fileHandleForWritingAtPath(pathStr)
        } catch (_: Exception) {
            // ignore exceptions
        }
    }

    @OptIn(BetaInteropApi::class)
    actual fun write(message: String) {
        val nsString = NSString.create(string = message)
        val data = nsString.dataUsingEncoding(NSUTF8StringEncoding) ?: return
        fileHandle?.writeData(data)
    }

    actual fun flush() {
        fileHandle?.synchronizeFile()
    }

    actual fun close() {
        fileHandle?.closeFile()
        fileHandle = null
    }
}

actual fun createPlatformBufferedWriter(path: Path): PlatformBufferedWriter = PlatformBufferedWriter(path)
