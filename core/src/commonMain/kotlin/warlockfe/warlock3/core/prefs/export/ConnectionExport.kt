package warlockfe.warlock3.core.prefs.export

data class ConnectionExport(
    val name: String,
    val username: String,
    val settings: Map<String, String>,
)
