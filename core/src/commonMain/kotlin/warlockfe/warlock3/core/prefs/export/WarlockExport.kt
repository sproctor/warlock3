package warlockfe.warlock3.core.prefs.export

data class WarlockExport(
    val accounts: List<AccountExport>,
    val characters: List<CharacterExport>,
    val settings: Map<String, String>,
)
