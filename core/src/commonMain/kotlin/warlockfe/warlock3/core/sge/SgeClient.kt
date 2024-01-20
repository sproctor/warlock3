package warlockfe.warlock3.core.sge

import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharedFlow

interface SgeClient {

    val eventFlow: SharedFlow<SgeEvent>

    suspend fun connect(): Result<Job>

    fun login(username: String, password: String)

    fun requestGameList()

    fun requestCharacterList()

    fun selectGame(gameCode: String)

    fun selectCharacter(characterCode: String)

    fun close()
}