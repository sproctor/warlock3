package warlockfe.warlock3.core.client

import warlockfe.warlock3.core.text.StyledString
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface WarlockClient {

    val maxTypeAhead: Int

    val eventFlow: SharedFlow<ClientEvent>

    val properties: StateFlow<ImmutableMap<String, String>>

    val components: StateFlow<ImmutableMap<String, StyledString>>

    val characterId: StateFlow<String?>

    val time: Long

    suspend fun disconnect()

    suspend fun sendCommand(line: String, echo: Boolean = true)

    suspend fun print(message: StyledString)

    suspend fun debug(message: String)
}
