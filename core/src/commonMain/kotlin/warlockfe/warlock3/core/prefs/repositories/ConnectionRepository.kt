package warlockfe.warlock3.core.prefs.repositories

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import warlockfe.warlock3.core.prefs.dao.ConnectionDao
import warlockfe.warlock3.core.prefs.mappers.toDomain
import warlockfe.warlock3.core.prefs.models.ConnectionEntity
import warlockfe.warlock3.core.sge.StoredConnection

class ConnectionRepository(
    private val connectionDao: ConnectionDao,
) {
    fun observeAllConnections(): Flow<List<StoredConnection>> {
        return connectionDao.observeAllWithDetails().map { connections ->
            connections.map { it.toDomain() }
        }
    }

    suspend fun deleteConnection(id: String) {
        connectionDao.delete(id)
    }

    suspend fun save(username: String, character: String, gameCode: String, name: String) {
        connectionDao.save(
            ConnectionEntity(
                id = "$gameCode:$character".lowercase(),
                username = username,
                character = character,
                gameCode = gameCode,
                name = name,
            )
        )
    }
}
