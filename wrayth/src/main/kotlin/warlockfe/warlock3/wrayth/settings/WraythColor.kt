package warlockfe.warlock3.wrayth.settings

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("i")
data class WraythColor(
    val id: String,
    val color: String,
)