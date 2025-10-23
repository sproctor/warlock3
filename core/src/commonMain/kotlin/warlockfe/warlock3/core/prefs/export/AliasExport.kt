package warlockfe.warlock3.core.prefs.export

data class AliasExport(
    val pattern: String, // pattern serves as unique id
    val replacement: String,
)
