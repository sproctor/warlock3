package cc.warlock.warlock3.core

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface WarlockClient {
    val eventFlow: SharedFlow<ClientEvent>

    val properties: StateFlow<Map<String, String>>

    val components: StateFlow<Map<String, StyledString>>

    fun disconnect()

    fun sendCommand(line: String, echo: Boolean = true)

    // fun send(toSend: String)

    fun print(message: StyledString)
}
