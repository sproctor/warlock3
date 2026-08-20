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
    var cursor = u32(eocd + 16) // central directory offset

    // Every offset and length below is a number the archive chose, so each is range checked before
    // it indexes anything. They stay Long until those checks pass: a field near 0xFFFFFFFF would
    // otherwise wrap to a negative Int and turn a rejectable archive into an indexing accident.
    val entries = LinkedHashMap<String, ByteArray>()
    var totalUncompressed = 0L
    repeat(totalEntries) {
        require(cursor + 46 <= bytes.size) { "Central directory header at $cursor runs past the end" }
        val header = cursor.toInt()
        require(u32(header) == CENTRAL_DIRECTORY_SIGNATURE) {
            "Invalid central directory header signature at $header"
        }
        val flags = u16(header + 8)
        val method = u16(header + 10)
        val compressedSize = u32(header + 20)
        val uncompressedSize = u32(header + 24)
        val nameLength = u16(header + 28)
        val extraLength = u16(header + 30)
        val commentLength = u16(header + 32)
        val localHeaderOffset = u32(header + 42)
        require(header + 46L + nameLength + extraLength + commentLength <= bytes.size) {
            "Central directory entry at $header runs past the end"
        }
        val name = bytes.decodeToString(header + 46, header + 46 + nameLength)
        cursor = header + 46L + nameLength + extraLength + commentLength

        if (name.endsWith("/")) return@repeat

        // Bit 0 of the general purpose flags marks an encrypted entry. Without this check its
        // ciphertext would be handed back as though it were the file's content.
        require(flags and FLAG_ENCRYPTED == 0) { "Encrypted entry \"$name\" is not supported" }

        // Refused here rather than after the entry's bytes have been copied out.
        require(method == METHOD_STORED || method == METHOD_DEFLATED) {
            "Unsupported compression method $method for \"$name\""
        }

        // Nothing was compressed, so the two sizes have to agree. Enforcing that is what lets the
        // budget below count uncompressedSize for every entry, including the stored ones whose
        // returned bytes are measured by compressedSize.
        require(method != METHOD_STORED || compressedSize == uncompressedSize) {
            "Stored entry \"$name\" declares mismatched compressed and uncompressed sizes"
        }

        // Checked against what the directory declares, before inflating anything, so an archive
        // that expands enormously is refused rather than decompressed and then measured.
        totalUncompressed += uncompressedSize
        require(totalUncompressed <= MAX_UNCOMPRESSED_BYTES) {
            "Zip expands to more than $MAX_UNCOMPRESSED_BYTES bytes"
        }

        // The central directory's name/extra lengths can differ from the local header's, so read the
        // local header to locate the actual start of the entry's data.
        require(localHeaderOffset + 30 <= bytes.size) { "Invalid local file header for \"$name\"" }
        val localHeader = localHeaderOffset.toInt()
        require(u32(localHeader) == LOCAL_FILE_HEADER_SIGNATURE) {
            "Invalid local file header for \"$name\""
        }

        // The flags appear in both headers, and only the central directory's were checked above.
        require(u16(localHeader + 6) and FLAG_ENCRYPTED == 0) {
            "Encrypted entry \"$name\" is not supported"
        }

        val localNameLength = u16(localHeader + 26)
        val localExtraLength = u16(localHeader + 28)
        val dataStart = localHeader + 30L + localNameLength + localExtraLength
        require(dataStart + compressedSize <= bytes.size) { "Entry \"$name\" runs past the end" }
        val start = dataStart.toInt()
        val data = bytes.copyOfRange(start, start + compressedSize.toInt())

        entries[name] = if (method == METHOD_STORED) data else inflate(data, uncompressedSize, name)
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
