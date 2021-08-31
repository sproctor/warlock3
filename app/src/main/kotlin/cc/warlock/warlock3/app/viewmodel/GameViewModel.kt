package cc.warlock.warlock3.app.viewmodel

import cc.warlock.warlock3.core.ClientEvent
import cc.warlock.warlock3.core.StyledString
import cc.warlock.warlock3.stormfront.network.StormfrontClient
import cc.warlock.warlock3.stormfront.protocol.StormfrontNodeVisitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class GameViewModel {
    private lateinit var client: StormfrontClient
    private val _lines = MutableStateFlow<List<StyledString>>(emptyList())
    val lines = _lines.asStateFlow()
    private val scope = CoroutineScope(Dispatchers.IO)

    fun connect(host: String, port: Int, key: String) {
        client = StormfrontClient(host, port)
        scope.launch {
            client.connect(key)
            client.eventFlow.collect { event ->
                when (event) {
                    is ClientEvent.ClientDataReceivedEvent ->
                        _lines.value = _lines.value + listOf(event.text)
                    is ClientEvent.ClientDataSentEvent ->
                        _lines.value = _lines.value + listOf(StyledString(event.text))
                    is ClientEvent.ClientDisconnectedEvent ->
                        _lines.value = _lines.value + listOf(StyledString("disconnected"))
                }
            }
        }
    }

    fun send(line: String) {
        client.sendCommand(line)
    }
}