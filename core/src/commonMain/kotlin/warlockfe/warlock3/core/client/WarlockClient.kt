package warlockfe.warlock3.core.client

import kotlinx.collections.immutable.ImmutableMap
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import warlockfe.warlock3.core.text.StyledString

interface WarlockClient {

    val eventFlow: SharedFlow<ClientEvent>

    val properties: StateFlow<ImmutableMap<String, String>>

    val components: StateFlow<ImmutableMap<String, StyledString>>

    val characterId: StateFlow<String?>

    val time: Long

    val connected: StateFlow<Boolean>

    suspend fun connect()

    fun disconnect()

    suspend fun sendCommand(line: String): SendCommandType

    suspend fun sendCommandDirect(command: String)

    suspend fun startScript(scriptCommand: String)

    suspend fun print(message: StyledString)

    suspend fun debug(message: String)

    suspend fun scriptDebug(message: String)

    fun setMaxTypeAhead(value: Int)

    fun close()
}
