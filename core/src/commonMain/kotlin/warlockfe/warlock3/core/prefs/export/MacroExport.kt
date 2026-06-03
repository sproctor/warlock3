package warlockfe.warlock3.core.prefs.export

import kotlinx.serialization.Serializable

@Serializable
data class MacroExport(
    // `key` is the authoritative binding string (e.g. "ctrl A"); it fully identifies the macro.
    // The legacy keyCode/modifier columns are deprecated and intentionally not exported.
    val key: String,
    val value: String,
)
