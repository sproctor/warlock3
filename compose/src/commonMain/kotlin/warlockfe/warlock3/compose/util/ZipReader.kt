package warlockfe.warlock3.compose.util

import okio.Buffer
import okio.Inflater
import okio.InflaterSource
import okio.buffer

// A minimal in-memory zip reader, adapted from the pure-Kotlin reader in kzip (MIT, Jonas
// Broeckmann; https://github.com/Jojo4GH/kzip/pull/6): it locates the End Of Central Directory
// record, walks the central directory, and reads each entry's data from its local header. DEFLATE
// entries are inflated with Okio; STORED entries are copied.
//
// This is shared rather than delegated to java.util.zip on JVM/Android, because iOS has no bundled
// zip facility and one implementation is easier to reason about than two that can diverge. Okio
// supplies only the DEFLATE primitive, which it backs with the platform's zlib on every target.

private const val EOCD_SIGNATURE = 0x06054b50L
private const val CENTRAL_DIRECTORY_SIGNATURE = 0x02014b50L
private const val LOCAL_FILE_HEADER_SIGNATURE = 0x04034b50L
private const val METHOD_STORED = 0
private const val METHOD_DEFLATED = 8
private const val FLAG_ENCRYPTED = 1

// A skin is a manifest plus a handful of images; the bundled one is ~210KB. This ceiling is here
// only so that a hostile archive cannot exhaust memory, since the user can point the skin setting
// at any file they have picked up.
private const val MAX_UNCOMPRESSED_BYTES = 128L * 1024 * 1024

/**
 * Reads the entries of a zip archive held entirely in memory, returning a map of entry name to its
 * raw bytes. Directory entries are omitted.
 */
fun readZipEntries(bytes: ByteArray): Map<String, ByteArray> {
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
        // A comment can contain these four bytes itself, so a signature alone is not enough:
        // accept a candidate only when the comment length it declares reaches the end of the file.
        if (u32(i) == EOCD_SIGNATURE && i + 22 + u16(i + 20) == bytes.size) {
            eocd = i
            break
        }
    }
    require(eocd >= 0) { "Not a ZIP file (EOCD not found)" }

    val totalEntries = u16(eocd + 10)
    var cursor = u32(eocd + 16).toInt() // central directory offset

    val entries = LinkedHashMap<String, ByteArray>()
    var totalUncompressed = 0L
    repeat(totalEntries) {
        require(u32(cursor) == CENTRAL_DIRECTORY_SIGNATURE) {
            "Invalid central directory header signature at $cursor"
        }
        val flags = u16(cursor + 8)
        val method = u16(cursor + 10)
        val compressedSize = u32(cursor + 20).toInt()
        val uncompressedSize = u32(cursor + 24)
        val nameLength = u16(cursor + 28)
        val extraLength = u16(cursor + 30)
        val commentLength = u16(cursor + 32)
        val localHeaderOffset = u32(cursor + 42).toInt()
        val name = bytes.decodeToString(cursor + 46, cursor + 46 + nameLength)
        cursor += 46 + nameLength + extraLength + commentLength

        if (name.endsWith("/")) return@repeat

        // Bit 0 of the general purpose flags marks an encrypted entry. Without this check its
        // ciphertext would be handed back as though it were the file's content.
        require(flags and FLAG_ENCRYPTED == 0) { "Encrypted entry \"$name\" is not supported" }

        // Checked against what the directory declares, before inflating anything, so an archive
        // that expands enormously is refused rather than decompressed and then measured.
        totalUncompressed += uncompressedSize
        require(totalUncompressed <= MAX_UNCOMPRESSED_BYTES) {
            "Zip expands to more than $MAX_UNCOMPRESSED_BYTES bytes"
        }

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
                METHOD_DEFLATED -> inflate(data, uncompressedSize, name)
                else -> throw IllegalArgumentException("Unsupported compression method $method for \"$name\"")
            }
    }
    return entries
}

/**
 * Inflates a zip entry's raw DEFLATE stream, which carries no zlib header — hence `nowrap`. Reads
 * exactly the [expectedSize] the central directory declared, so the entry cannot expand past it.
 */
private fun inflate(
    data: ByteArray,
    expectedSize: Long,
    name: String,
): ByteArray {
    val inflater = Inflater(true)
    try {
        val source = InflaterSource(Buffer().apply { write(data) }, inflater).buffer()
        val inflated = source.readByteArray(expectedSize)
        require(source.exhausted()) { "Entry \"$name\" inflates past its declared size" }
        return inflated
    } finally {
        inflater.end()
    }
}
