package warlockfe.warlock3.wrayth.settings

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("h")
data class WraythHighlight(
    val text: String,
    val color: String?,
    val bgcolor: String?,
)