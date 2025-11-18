package warlockfe.warlock3.core.sge

import kotlinx.coroutines.flow.SharedFlow

interface SgeClient {

    val eventFlow: SharedFlow<SgeEvent>

    suspend fun connect(settings: SgeSettings): Boolean

    suspend fun login(username: String, password: String)

    suspend fun requestGameList()

    suspend fun requestCharacterList()

    suspend fun selectGame(gameCode: String)

    suspend fun selectCharacter(characterCode: String)

    suspend fun autoConnect(settings: SgeSettings, connection: StoredConnection): AutoConnectResult

    fun close()
}

sealed interface AutoConnectResult {
    data class Failure(val reason: String) : AutoConnectResult

    data class Success(val credentials: SimuGameCredentials) : AutoConnectResult
}