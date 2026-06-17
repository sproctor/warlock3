package warlockfe.warlock3.core.prefs.repositories

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import warlockfe.warlock3.core.mudmobile.MudMobileCharacter
import warlockfe.warlock3.core.prefs.config.ClientConfigStore
import warlockfe.warlock3.core.prefs.config.ConnectionConfig
import warlockfe.warlock3.core.prefs.config.toStoredConnection
import warlockfe.warlock3.core.prefs.dao.AccountDao
import warlockfe.warlock3.core.sge.StoredConnection

/**
 * Saved connection profiles, stored in `connections.toml` via [ClientConfigStore]. The account
 * password is looked up from the SQLite account table when building a [StoredConnection] (credentials
 * deliberately stay out of plaintext TOML).
 */
class ConnectionRepository(
    private val store: ClientConfigStore,
    private val accountDao: AccountDao,
) {
    fun observeAllConnections(): Flow<List<StoredConnection>> =
        store.observeConnections().map { registry ->
            registry.connections.map { connection ->
                connection.toStoredConnection(accountDao.getByUsername(connection.username)?.password)
            }
        }

    suspend fun deleteConnection(name: String) {
        // Matches the old DAO behavior: delete by id (the argument is the connection id).
        store.mutateConnections { registry ->
            registry.copy(connections = registry.connections.filterNot { it.id == name })
        }
    }

    suspend fun rename(
        oldName: String,
        newName: String,
    ) {
        store.mutateConnections { registry ->
            registry.copy(
                connections = registry.connections.map { if (it.name == oldName) it.copy(name = newName) else it },
            )
        }
    }

    suspend fun renameById(
        id: String,
        newName: String,
    ) {
        store.mutateConnections { registry ->
            registry.copy(
                connections = registry.connections.map { if (it.id == id) it.copy(name = newName) else it },
            )
        }
    }

    suspend fun save(
        username: String,
        character: String,
        gameCode: String,
        name: String,
    ) {
        val id = "$gameCode:$character".lowercase()
        store.mutateConnections { registry ->
            // Preserve any existing proxy settings on this connection; only identity fields change.
            val existing = registry.connections.firstOrNull { it.id == id }
            val updated =
                (existing ?: ConnectionConfig(id = id)).copy(
                    name = name,
                    username = username,
                    gameCode = gameCode,
                    character = character,
                )
            registry.copy(connections = registry.connections.filterNot { it.id == id } + updated)
        }
    }

    /**
     * Reconcile the MUD Mobile connections in the registry with the given set, **preserving the
     * existing order** (so a user's manual reordering survives a refresh): existing entries are
     * updated in place, MUD Mobile entries no longer present are dropped, and genuinely new ones are
     * appended at the end. The MUD Mobile character id is the connection id, so it maps to the API.
     */
    suspend fun syncMudMobileConnections(characters: List<MudMobileCharacter>) {
        store.mutateConnections { registry ->
            val byId = characters.associateBy { it.id }
            val existingIds = registry.connections.mapTo(mutableSetOf()) { it.id }
            val kept =
                registry.connections
                    // Drop MUD Mobile rows that are no longer in the fetched set.
                    .filter { !it.mudMobile || byId.containsKey(it.id) }
                    // Refresh the fields of surviving MUD Mobile rows, keeping their position.
                    .map { connection ->
                        if (!connection.mudMobile) {
                            connection
                        } else {
                            byId.getValue(connection.id).toMudMobileConfig()
                        }
                    }
            val added = characters.filterNot { it.id in existingIds }.map { it.toMudMobileConfig() }
            registry.copy(connections = kept + added)
        }
    }

    /**
     * Persist a new connection order. [orderedIds] is the full set of connection ids in the desired
     * order; any connection not named in it (shouldn't normally happen) is kept at the end so a
     * stale list can never drop rows.
     */
    suspend fun reorderConnections(orderedIds: List<String>) {
        store.mutateConnections { registry ->
            val byId = registry.connections.associateBy { it.id }
            val reordered = orderedIds.mapNotNull { byId[it] }
            val missing = registry.connections.filter { it.id !in orderedIds }
            registry.copy(connections = reordered + missing)
        }
    }

    /** Drop all MUD Mobile connections (e.g. when the user disconnects their MUD Mobile account). */
    suspend fun removeMudMobileConnections() {
        store.mutateConnections { registry ->
            registry.copy(connections = registry.connections.filterNot { it.mudMobile })
        }
    }

    suspend fun getByName(name: String): StoredConnection? {
        val connection = store.currentConnections().connections.firstOrNull { it.name == name } ?: return null
        return connection.toStoredConnection(accountDao.getByUsername(connection.username)?.password)
    }
}

private fun MudMobileCharacter.toMudMobileConfig(): ConnectionConfig =
    ConnectionConfig(
        id = id,
        name = characterName,
        username = account,
        gameCode = game,
        character = characterName,
        characterCode = characterCode,
        mudMobile = true,
    )
