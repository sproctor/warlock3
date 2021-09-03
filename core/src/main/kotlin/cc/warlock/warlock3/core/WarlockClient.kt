package cc.warlock.warlock3.core

import kotlinx.coroutines.flow.SharedFlow

interface WarlockClient {
    val eventFlow: SharedFlow<ClientEvent>

    fun disconnect()

    fun sendCommand(line: String)

    fun send(toSend: String)

    fun print(message: StyledString)
}
