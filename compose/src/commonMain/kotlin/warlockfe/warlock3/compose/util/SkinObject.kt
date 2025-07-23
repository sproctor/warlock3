package warlockfe.warlock3.compose.util

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
    val type: String?,
    val data: String?,
)
