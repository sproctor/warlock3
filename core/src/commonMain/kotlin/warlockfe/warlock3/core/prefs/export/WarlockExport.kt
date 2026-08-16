package warlockfe.warlock3.core.prefs.export

import kotlinx.serialization.Serializable

/**
 * A full backup. Accounts are deliberately absent: an account is a username plus a password, the
 * password is never exported, and the username alone is already carried by every connection that
 * uses it. Import reconstructs the account rows from [connections] instead.
 */
@Serializable
data class WarlockExport(
    val characters: List<CharacterExport>,
    val connections: List<ConnectionExport> = emptyList(),
    val settings: Map<String, String>,
)
