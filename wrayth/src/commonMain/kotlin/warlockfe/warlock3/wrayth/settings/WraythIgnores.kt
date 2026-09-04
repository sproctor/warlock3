package warlockfe.warlock3.wrayth.settings

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlSerialName

/**
 * The `<ignores disable="n">` section of a Wrayth settings file. Entries are the same `<h>` element
 * the highlight sections use. [disable] is the dialog's master "Disable" checkbox; we have no
 * equivalent toggle, so a disabled section's entries are skipped on import instead of arriving as
 * active rules the source client wasn't applying.
 */
@Serializable
@XmlSerialName("ignores")
data class WraythIgnores(
    val disable: String? = null,
    @XmlSerialName("h")
    val entries: List<WraythHighlight> = emptyList(),
)
