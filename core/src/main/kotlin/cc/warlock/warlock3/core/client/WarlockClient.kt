package cc.warlock.warlock3.core.client

import cc.warlock.warlock3.core.text.StyledString
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface WarlockClient {

    val maxTypeAhead: Int

    val eventFlow: SharedFlow<ClientEvent>

    val properties: StateFlow<Map<String, String>>

    val components: StateFlow<Map<String, StyledString>>

    val characterId: StateFlow<String?>

    val time: Long

    fun disconnect()

    fun sendCommand(line: String, echo: Boolean = true)

    // fun send(toSend: String)

    fun print(message: StyledString)
}
