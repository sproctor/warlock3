package warlockfe.warlock3.compose.model

import kotlinx.serialization.Serializable

@Serializable
data class SkinObject(
    val top: Int? = null,
    val left: Int? = null,
    val width: Int? = null,
    val height: Int? = null,
    val color: SkinColor? = null,
    val bar: SkinColor? = null,
    val background: SkinColor? = null,
    val image: SkinImage? = null,
    // Compass: key of the sibling child whose image is the sprite sheet this direction slices from.
    val sprite: String? = null,
    // Text-style fields, used by the "presets" section (color = text color, background = background).
    val bold: Boolean? = null,
    val italic: Boolean? = null,
    val underline: Boolean? = null,
    val entireLine: Boolean? = null,
    val fontFamily: String? = null,
    val fontSize: Float? = null,
    val fontWeight: Int? = null,
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
    // Name of a bundled Compose drawable resource (a `Res.allDrawableResources` key) to use as the
    // image, for skin entries that reference a built-in asset instead of carrying their own bytes.
    val name: String? = null,
)
