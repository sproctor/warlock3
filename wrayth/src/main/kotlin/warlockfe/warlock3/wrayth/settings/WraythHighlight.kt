package warlockfe.warlock3.wrayth.settings

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlSerialName

@Serializable
@XmlSerialName("h")
data class WraythHighlight(
    val text: String? = null,
    val color: String? = null,
    val bgcolor: String? = null,
    val line: String? = null,
)