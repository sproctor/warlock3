package warlockfe.warlock3.compose.model

import kotlinx.serialization.Serializable

@Serializable
data class SkinObject(
    val top: Int? = null,
    val left: Int? = null,
    val width: Int? = null,
    val height: Int? = null,
    val color: String? = null,
    val bar: String? = null,
    val background: String? = null,
    val image: SkinImage? = null,
    val children: Map<String, SkinObject> = emptyMap(),
)

@Serializable
data class SkinImage(
    val type: String? = null,
    // Base64-encoded image bytes. Inlined directly in a .json skin, or resolved from [file] when
    // the skin is loaded from a .zip.
    val data: String? = null,
    // Name of a file inside the skin .zip to load the image bytes from. Ignored when [data] is set.
    val file: String? = null,
)
