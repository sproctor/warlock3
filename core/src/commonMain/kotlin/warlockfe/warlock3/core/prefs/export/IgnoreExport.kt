package warlockfe.warlock3.core.prefs.export

import kotlinx.serialization.Serializable

@Serializable
data class IgnoreExport(
    val pattern: String, // pattern serves as unique id
    val isRegex: Boolean,
    // "contains", "word", or "line"; decoded leniently (unknown falls back to "contains").
    val mode: String,
    val ignoreCase: Boolean,
)
