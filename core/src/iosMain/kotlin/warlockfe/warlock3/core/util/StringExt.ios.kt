package warlockfe.warlock3.core.util

import io.ktor.utils.io.charsets.Charset
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSWindowsCP1252StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Foundation.dataWithBytes
import platform.posix.memcpy

@OptIn(BetaInteropApi::class)
actual fun ByteArray.decodeWindows1252(
    offset: Int,
    length: Int,
): String {
    val nsData = toNSData(offset, length)
    return NSString.create(nsData, NSWindowsCP1252StringEncoding).toString()
}

@OptIn(ExperimentalForeignApi::class)
fun ByteArray.toNSData(
    offset: Int,
    length: Int,
): NSData {
    return this.usePinned { pinned ->
        NSData.dataWithBytes(bytes = pinned.addressOf(offset), length = length.toULong())
    }
}

@OptIn(BetaInteropApi::class)
actual fun String.encodeWindows1252(): ByteArray {
    val nsString = NSString.create(this)
    val nsData = nsString.dataUsingEncoding(NSWindowsCP1252StringEncoding)
    return nsData?.toByteArray() ?: ByteArray(0)
}

@OptIn(ExperimentalForeignApi::class)
fun NSData.toByteArray(): ByteArray {
    // If the NSData is empty, return an empty ByteArray to avoid issues with memcpy
    if (this.length.toInt() == 0) {
        return ByteArray(0)
    }

    // Create a ByteArray of the same length as the NSData
    return ByteArray(this.length.toInt()).apply {
        // Use usePinned to safely access the memory address of the ByteArray
        usePinned { pinned ->
            // Copy the bytes from NSData to the ByteArray
            // memcpy(destination, source, number_of_bytes)
            memcpy(pinned.addressOf(0), this@toByteArray.bytes, this@toByteArray.length)
        }
    }
}
