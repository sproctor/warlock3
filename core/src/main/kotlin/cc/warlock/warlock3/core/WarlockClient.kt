package cc.warlock.warlock3.core

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.net.Socket

interface WarlockClient {
    val eventFlow: SharedFlow<ClientEvent>

    fun disconnect()

    fun send(toSend: String)

}
