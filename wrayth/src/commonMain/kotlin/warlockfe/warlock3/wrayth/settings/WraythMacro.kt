package warlockfe.warlock3.wrayth.settings

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlSerialName

@Serializable
@XmlSerialName("k")
data class WraythMacro(val key: String, val action: String)
