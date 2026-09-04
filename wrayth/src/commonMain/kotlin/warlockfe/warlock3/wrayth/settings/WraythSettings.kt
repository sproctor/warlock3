package warlockfe.warlock3.wrayth.settings

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlChildrenName

@Serializable
@SerialName("settings")
data class WraythSettings(
    val client: String? = null,
    @XmlChildrenName("strings")
    val strings: List<WraythHighlight>,
    @XmlChildrenName("names")
    val names: List<WraythHighlight>,
    // Absent in very old settings files.
    val ignores: WraythIgnores? = null,
    @XmlChildrenName("palette")
    val palette: List<WraythColor>,
    @XmlChildrenName("keys")
    val macros: List<WraythMacroSet>,
)
