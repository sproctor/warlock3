package warlockfe.warlock3.wrayth.settings

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import warlockfe.warlock3.wrayth.settings.WraythHighlight

@Serializable
@SerialName("settings")
data class WraythSettings(
    val strings: List<WraythHighlight>,
    val names: List<WraythHighlight>,
    val palette: List<WraythColor>,
)