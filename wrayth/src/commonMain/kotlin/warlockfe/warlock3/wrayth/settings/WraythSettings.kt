package warlockfe.warlock3.wrayth.settings

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlChildrenName
import warlockfe.warlock3.wrayth.settings.WraythHighlight

@Serializable
@SerialName("settings")
data class WraythSettings(
    val client: String? = null,
    @XmlChildrenName("strings")
    val strings: List<WraythHighlight>,
    @XmlChildrenName("names")
    val names: List<WraythHighlight>,
    @XmlChildrenName("palette")
    val palette: List<WraythColor>,
)