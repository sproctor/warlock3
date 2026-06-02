package warlockfe.warlock3.compose.util

import dev.karmakrafts.kompress.Inflater

// iOS has no bundled zip facility. This is a minimal in-memory zip reader adapted from the
// pure-Kotlin reader in kzip (MIT, Jonas Broeckmann; https://github.com/Jojo4GH/kzip/pull/6): it
// locates the End Of Central Directory record, walks the central directory, and reads each entry's
// data from its local header. DEFLATE entries are inflated with kompress; STORED entries are copied.

private const val EOCD_SIGNATURE = 0x06054b50L
private const val CENTRAL_DIRECTORY_SIGNATURE = 0x02014b50L
private const val LOCAL_FILE_HEADER_SIGNATURE = 0x04034b50L
private const val METHOD_STORED = 0
private const val METHOD_DEFLATED = 8

actual fun readZipEntries(bytes: ByteArray): Map<String, ByteArray> {
    fun u16(i: Int): Int = (bytes[i].toInt() and 0xFF) or ((bytes[i + 1].toInt() and 0xFF) shl 8)

    fun u32(i: Int): Long =
        (bytes[i].toLong() and 0xFF) or
            ((bytes[i + 1].toLong() and 0xFF) shl 8) or
            ((bytes[i + 2].toLong() and 0xFF) shl 16) or
            ((bytes[i + 3].toLong() and 0xFF) shl 24)

    require(bytes.size >= 22) { "Not a ZIP file (too small)" }

    // The EOCD record is at the end, before an optional comment of up to 0xFFFF bytes.
    var eocd = -1
    val searchFloor = maxOf(0, bytes.size - (0xFFFF + 22))
    for (i in bytes.size - 22 downTo searchFloor) {
        if (u32(i) == EOCD_SIGNATURE) {
            eocd = i
            break
        }
    }
    require(eocd >= 0) { "Not a ZIP file (EOCD not found)" }

    val totalEntries = u16(eocd + 10)
    var cursor = u32(eocd + 16).toInt() // central directory offset

    val entries = LinkedHashMap<String, ByteArray>()
    repeat(totalEntries) {
        require(u32(cursor) == CENTRAL_DIRECTORY_SIGNATURE) {
            "Invalid central directory header signature at $cursor"
        }
        val method = u16(cursor + 10)
        val compressedSize = u32(cursor + 20).toInt()
        val nameLength = u16(cursor + 28)
        val extraLength = u16(cursor + 30)
        val commentLength = u16(cursor + 32)
        val localHeaderOffset = u32(cursor + 42).toInt()
        val name = bytes.decodeToString(cursor + 46, cursor + 46 + nameLength)
        cursor += 46 + nameLength + extraLength + commentLength

        if (name.endsWith("/")) return@repeat

        // The central directory's name/extra lengths can differ from the local header's, so read the
        // local header to locate the actual start of the entry's data.
        require(u32(localHeaderOffset) == LOCAL_FILE_HEADER_SIGNATURE) {
            "Invalid local file header for \"$name\""
        }
        val localNameLength = u16(localHeaderOffset + 26)
        val localExtraLength = u16(localHeaderOffset + 28)
        val dataStart = localHeaderOffset + 30 + localNameLength + localExtraLength
        val data = bytes.copyOfRange(dataStart, dataStart + compressedSize)

        entries[name] =
            when (method) {
                METHOD_STORED -> data
                METHOD_DEFLATED -> Inflater.inflate(data = data, raw = true)
                else -> throw IllegalArgumentException("Unsupported compression method $method for \"$name\"")
            }
    }
    return entries
}
