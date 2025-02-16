package warlockfe.warlock3.core.sge

import kotlinx.coroutines.flow.SharedFlow

interface SgeClient {

    val eventFlow: SharedFlow<SgeEvent>

    suspend fun connect(): Boolean

    suspend fun login(username: String, password: String)

    suspend fun requestGameList()

    suspend fun requestCharacterList()

    suspend fun selectGame(gameCode: String)

    suspend fun selectCharacter(characterCode: String)

    fun close()
}