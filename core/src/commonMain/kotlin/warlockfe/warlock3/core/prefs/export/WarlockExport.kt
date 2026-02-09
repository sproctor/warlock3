package warlockfe.warlock3.core.prefs.export

import kotlinx.serialization.Serializable

@Serializable
data class WarlockExport(
    val accounts: List<AccountExport>,
    val characters: List<CharacterExport>,
    val settings: Map<String, String>,
)
