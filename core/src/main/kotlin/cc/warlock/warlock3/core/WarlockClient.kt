package cc.warlock.warlock3.core

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface WarlockClient {
    val eventFlow: SharedFlow<ClientEvent>

    val properties: StateFlow<Map<String, String>>

    val components: StateFlow<Map<String, StyledString>>

    val variables: StateFlow<Map<String, String>>

    fun disconnect()

    fun sendCommand(line: String, echo: Boolean = true)

    // fun send(toSend: String)

    fun print(message: StyledString)

    fun setVariable(name: String, value: String)

    fun deleteVariable(name: String)
}
