import warlockfe.warlock3.compose.util.SkinLoader
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SkinZipLoaderTest {
    private fun zipOf(entries: Map<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun storedZipOf(entries: Map<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.setMethod(ZipOutputStream.STORED)
            entries.forEach { (name, bytes) ->
                val entry = ZipEntry(name)
                entry.size = bytes.size.toLong()
                entry.compressedSize = bytes.size.toLong()
                entry.crc = CRC32().apply { update(bytes) }.value
                zip.putNextEntry(entry)
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun zipWithComment(
        entries: Map<String, ByteArray>,
        comment: String,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.setComment(comment)
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    /** Offset of the first central directory header, whose fields the tests below tamper with. */
    private fun ByteArray.centralDirectoryOffset(): Int =
        indices.first { i ->
            i + 4 <= size &&
                this[i] == 0x50.toByte() &&
                this[i + 1] == 0x4B.toByte() &&
                this[i + 2] == 0x01.toByte() &&
                this[i + 3] == 0x02.toByte()
        }

    /** Offset of the first local file header, which the tests below tamper with. */
    private fun ByteArray.localHeaderOffset(): Int =
        indices.first { i ->
            i + 4 <= size &&
                this[i] == 0x50.toByte() &&
                this[i + 1] == 0x4B.toByte() &&
                this[i + 2] == 0x03.toByte() &&
                this[i + 3] == 0x04.toByte()
        }

    private fun ByteArray.putU16(
        offset: Int,
        value: Int,
    ) {
        this[offset] = (value and 0xFF).toByte()
        this[offset + 1] = ((value shr 8) and 0xFF).toByte()
    }

    private fun ByteArray.putU32(
        offset: Int,
        value: Long,
    ) {
        for (byte in 0..3) {
            this[offset + byte] = ((value shr (8 * byte)) and 0xFF).toByte()
        }
    }

    @Test
    fun plain_json_still_parses() {
        val bytes = """{ "skin": { "top": 10 } }""".toByteArray()

        val skin = SkinLoader.parse(bytes)

        assertEquals(10, skin["skin"]?.top)
    }

    @Test
    fun zip_skin_resolves_file_reference_to_base64() {
        val imageBytes = byteArrayOf(1, 2, 3, 4, 5)
        val skinJson =
            """
            {
                "injury1": {
                    "top": 5,
                    "image": { "type": "image/png", "file": "injury1.png" }
                }
            }
            """.trimIndent()
        val zip =
            zipOf(
                mapOf(
                    "skin.json" to skinJson.toByteArray(),
                    "injury1.png" to imageBytes,
                ),
            )

        val skin = SkinLoader.parse(zip)

        val image = skin["injury1"]?.image
        assertEquals(5, skin["injury1"]?.top)
        assertEquals("image/png", image?.type)
        assertEquals(Base64.encode(imageBytes), image?.data)
    }

    @Test
    fun zip_skin_resolves_nested_child_file_reference() {
        val imageBytes = byteArrayOf(9, 8, 7)
        val skinJson =
            """
            {
                "compass": {
                    "children": {
                        "n": { "image": { "file": "north.png" } }
                    }
                }
            }
            """.trimIndent()
        val zip =
            zipOf(
                mapOf(
                    "skin.json" to skinJson.toByteArray(),
                    "north.png" to imageBytes,
                ),
            )

        val skin = SkinLoader.parse(zip)

        val childImage = skin["compass"]?.children?.get("n")?.image
        assertEquals(Base64.encode(imageBytes), childImage?.data)
    }

    @Test
    fun inline_data_takes_precedence_over_missing_file() {
        val skinJson =
            """
            {
                "injury1": { "image": { "data": "already-here", "file": "missing.png" } }
            }
            """.trimIndent()
        val zip = zipOf(mapOf("skin.json" to skinJson.toByteArray()))

        val skin = SkinLoader.parse(zip)

        assertEquals("already-here", skin["injury1"]?.image?.data)
    }

    @Test
    fun stored_zip_skin_resolves_through_skin_loader() {
        // The bundled default skin is stored uncompressed; make sure that format loads end-to-end.
        val imageBytes = byteArrayOf(10, 20, 30)
        val skinJson = """{ "injury1": { "image": { "file": "images/injury1.png" } } }"""
        val zip =
            storedZipOf(
                mapOf(
                    "skin.json" to skinJson.toByteArray(),
                    "images/injury1.png" to imageBytes,
                ),
            )

        val skin = SkinLoader.parse(zip)

        assertEquals(Base64.encode(imageBytes), skin["injury1"]?.image?.data)
    }

    @Test
    fun eocd_signature_inside_the_archive_comment_is_not_mistaken_for_the_real_one() {
        // The reader scans backwards from the end, so a comment carrying these four bytes is found
        // before the actual End Of Central Directory record. Padding puts it far enough from the
        // end to fall inside the search window.
        val skinJson = """{ "injury1": { "top": 7 } }"""
        val zip = zipWithComment(mapOf("skin.json" to skinJson.toByteArray()), "PK" + "x".repeat(40))

        val skin = SkinLoader.parse(zip)

        assertEquals(7, skin["injury1"]?.top)
    }

    @Test
    fun encrypted_entry_is_rejected_rather_than_returned_as_ciphertext() {
        val zip = zipOf(mapOf("skin.json" to """{ "injury1": { "top": 1 } }""".toByteArray()))
        // java.util.zip cannot write an encrypted entry, so set the flag the reader checks.
        val centralDirectory = zip.centralDirectoryOffset()
        zip.putU16(centralDirectory + 8, 1)

        val error = assertFailsWith<IllegalArgumentException> { SkinLoader.parse(zip) }

        assertEquals("""Encrypted entry "skin.json" is not supported""", error.message)
    }

    @Test
    fun entry_declaring_a_huge_expansion_is_rejected_before_inflating() {
        val zip = zipOf(mapOf("skin.json" to """{ "injury1": { "top": 1 } }""".toByteArray()))
        val centralDirectory = zip.centralDirectoryOffset()
        zip.putU32(centralDirectory + 24, 0x7FFFFFF0L) // uncompressed size

        assertFailsWith<IllegalArgumentException> { SkinLoader.parse(zip) }
    }

    @Test
    fun stored_entry_whose_sizes_disagree_is_rejected() {
        // A stored entry is returned as compressedSize bytes, so a small declared uncompressed size
        // would otherwise let it slip past the budget that counts uncompressedSize.
        val zip = storedZipOf(mapOf("skin.json" to """{ "injury1": { "top": 1 } }""".toByteArray()))
        val centralDirectory = zip.centralDirectoryOffset()
        zip.putU32(centralDirectory + 24, 4L) // uncompressed size, no longer matching compressed

        val error = assertFailsWith<IllegalArgumentException> { SkinLoader.parse(zip) }

        assertEquals(
            """Stored entry "skin.json" declares mismatched compressed and uncompressed sizes""",
            error.message,
        )
    }

    @Test
    fun entry_that_inflates_past_its_declared_size_is_rejected() {
        val zip = zipOf(mapOf("skin.json" to """{ "injury1": { "top": 1, "left": 2, "width": 3 } }""".toByteArray()))
        val centralDirectory = zip.centralDirectoryOffset()
        zip.putU32(centralDirectory + 24, 4L) // uncompressed size, far below the truth

        val error = assertFailsWith<IllegalArgumentException> { SkinLoader.parse(zip) }

        assertEquals("""Entry "skin.json" inflates past its declared size""", error.message)
    }

    @Test
    fun entry_encrypted_only_in_its_local_header_is_rejected() {
        // The flags live in both headers. An archive that clears the bit centrally and sets it
        // locally would otherwise have its ciphertext returned as content.
        val zip = zipOf(mapOf("skin.json" to """{ "injury1": { "top": 1 } }""".toByteArray()))
        zip.putU16(zip.localHeaderOffset() + 6, 1)

        val error = assertFailsWith<IllegalArgumentException> { SkinLoader.parse(zip) }

        assertEquals("""Encrypted entry "skin.json" is not supported""", error.message)
    }

    @Test
    fun unsupported_compression_method_is_rejected_before_its_data_is_copied() {
        val zip = zipOf(mapOf("skin.json" to """{ "injury1": { "top": 1 } }""".toByteArray()))
        zip.putU16(zip.centralDirectoryOffset() + 10, 99)
        // Oversized too, so the method has to be rejected first for this to report the method at
        // all: copying the payload before looking at the method fails on the range instead.
        zip.putU32(zip.centralDirectoryOffset() + 20, 0x7FFFFFF0L)

        val error = assertFailsWith<IllegalArgumentException> { SkinLoader.parse(zip) }

        assertEquals("""Unsupported compression method 99 for "skin.json"""", error.message)
    }

    @Test
    fun entry_pointing_past_the_end_of_the_archive_is_rejected() {
        val zip = zipOf(mapOf("skin.json" to """{ "injury1": { "top": 1 } }""".toByteArray()))
        // A local header offset near the 32-bit ceiling, which must be refused rather than wrapped
        // into a negative index.
        zip.putU32(zip.centralDirectoryOffset() + 42, 0xFFFFFFF0L)

        val error = assertFailsWith<IllegalArgumentException> { SkinLoader.parse(zip) }

        assertEquals("""Invalid local file header for "skin.json"""", error.message)
    }

    @Test
    fun central_directory_pointing_past_the_end_of_the_archive_is_rejected() {
        val zip = zipOf(mapOf("skin.json" to """{ "injury1": { "top": 1 } }""".toByteArray()))
        val eocd = zip.size - 22
        zip.putU32(eocd + 16, 0xFFFFFFF0L) // central directory offset

        assertFailsWith<IllegalArgumentException> { SkinLoader.parse(zip) }
    }

    @Test
    fun unresolved_file_reference_leaves_data_null() {
        val skinJson =
            """
            {
                "injury1": { "image": { "file": "missing.png" } }
            }
            """.trimIndent()
        val zip = zipOf(mapOf("skin.json" to skinJson.toByteArray()))

        val skin = SkinLoader.parse(zip)

        assertNull(skin["injury1"]?.image?.data)
    }
}
