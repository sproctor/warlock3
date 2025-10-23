package warlockfe.warlock3.core.prefs.export

data class MacroExport(
    val value: String,
    // All of these values make up the id
    val keyCode: Long,
    val ctrl: Boolean,
    val alt: Boolean,
    val shift: Boolean,
    val meta: Boolean,
)
