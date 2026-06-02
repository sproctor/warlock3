import warlockfe.warlock3.compose.util.SkinLoader
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
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
