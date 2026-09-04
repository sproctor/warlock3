package warlockfe.warlock3.wrayth.settings

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlSerialName

// The word/case semantics were verified against Wrayth 1.0.1.28 by toggling the dialog checkboxes and
// diffing its exported XML: word="y" is "Match Partial Word(s)" checked (absent = whole-word matching),
// case="y" is "Ignore Case" checked (absent = case-sensitive).
@Serializable
@XmlSerialName("h")
data class WraythHighlight(
    val text: String? = null,
    val color: String? = null,
    val bgcolor: String? = null,
    val line: String? = null,
    val sound: String? = null,
    val word: String? = null,
    val case: String? = null,
)
