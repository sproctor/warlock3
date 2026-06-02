package warlockfe.warlock3.compose.util

import kotlinx.serialization.json.Json
import warlockfe.warlock3.compose.model.SkinImage
import warlockfe.warlock3.compose.model.SkinObject
import kotlin.io.encoding.Base64

/**
 * Parses a skin from raw bytes. The bytes may be either:
 *  - a plain `.json` skin (a map of name to [SkinObject]), or
 *  - a `.zip` archive containing a `.json` skin plus any files it references.
 *
 * In a zip skin, a [SkinImage] may reference an archived file by name via [SkinImage.file] instead
 * of inlining base64 [SkinImage.data]; the referenced bytes are read and inlined at load time so the
 * rest of the app sees the same resolved [SkinObject] map regardless of source format.
 */
object SkinLoader {
    private val json =
        Json {
            ignoreUnknownKeys = true
        }

    fun parse(bytes: ByteArray): Map<String, SkinObject> =
        if (bytes.looksLikeZip()) {
            parseZip(bytes)
        } else {
            json.decodeFromString(bytes.decodeToString())
        }

    private fun parseZip(bytes: ByteArray): Map<String, SkinObject> {
        val entries = readZipEntries(bytes)
        val skinJson =
            entries.entries.firstOrNull { it.key.equals("skin.json", ignoreCase = true) }?.value
                ?: entries.entries.firstOrNull { it.key.endsWith(".json", ignoreCase = true) }?.value
                ?: throw IllegalArgumentException("Zip skin does not contain a .json file")
        val skin = json.decodeFromString<Map<String, SkinObject>>(skinJson.decodeToString())
        return skin.mapValues { (_, skinObject) -> skinObject.resolveFiles(entries) }
    }
}

private fun SkinObject.resolveFiles(entries: Map<String, ByteArray>): SkinObject =
    copy(
        image = image?.resolveFile(entries),
        children = children.mapValues { (_, child) -> child.resolveFiles(entries) },
    )

private fun SkinImage.resolveFile(entries: Map<String, ByteArray>): SkinImage {
    val reference = file
    if (reference == null || data != null) return this
    val fileBytes =
        entries[reference]
            ?: entries.entries.firstOrNull { it.key.equals(reference, ignoreCase = true) }?.value
            ?: return this
    return copy(data = Base64.encode(fileBytes))
}

// Zip archives start with the local-file-header signature "PK" (or the empty-archive and
// spanned variants).
private fun ByteArray.looksLikeZip(): Boolean =
    size >= 4 &&
        this[0] == 'P'.code.toByte() &&
        this[1] == 'K'.code.toByte() &&
        (this[2] == 0x03.toByte() || this[2] == 0x05.toByte() || this[2] == 0x07.toByte())
