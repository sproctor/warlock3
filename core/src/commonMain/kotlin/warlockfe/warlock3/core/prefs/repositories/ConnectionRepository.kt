package warlockfe.warlock3.core.prefs.repositories

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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

    suspend fun getByName(name: String): StoredConnection? {
        val connection = store.currentConnections().connections.firstOrNull { it.name == name } ?: return null
        return connection.toStoredConnection(accountDao.getByUsername(connection.username)?.password)
    }
}
