package warlockfe.warlock3.wrayth.settings

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlSerialName

@Serializable
@XmlSerialName("keys")
data class WraythMacroSet(
    val id: String,
    val name: String,
    @XmlSerialName("k")
    val macros: List<WraythMacro>
)
