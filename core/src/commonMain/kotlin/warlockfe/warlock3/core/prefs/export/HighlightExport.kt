package warlockfe.warlock3.core.prefs.export

import kotlinx.serialization.Serializable

@Serializable
data class HighlightExport(
    val pattern: String, // pattern serves as unique id
    val isRegex: Boolean,
    val matchPartialWord: Boolean,
    val ignoreCase: Boolean,
    val sound: String?,
    val styles: Map<Int, StyleExport>,
)
