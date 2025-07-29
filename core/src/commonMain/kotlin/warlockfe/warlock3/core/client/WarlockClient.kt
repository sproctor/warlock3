package warlockfe.warlock3.core.client

import kotlinx.collections.immutable.ImmutableMap
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import warlockfe.warlock3.core.text.StyledString
import warlockfe.warlock3.core.window.TextStream
import java.io.InputStream
import java.net.Socket

interface WarlockClient {

    val eventFlow: SharedFlow<ClientEvent>

    val properties: StateFlow<ImmutableMap<String, String>>

    val components: StateFlow<ImmutableMap<String, StyledString>>

    val menuData: StateFlow<WarlockMenuData>

    val characterId: StateFlow<String?>

    val time: Long

    val disconnected: StateFlow<Boolean>

    suspend fun connect(inputStream: InputStream, socket: Socket?, key: String)

    fun disconnect()

    suspend fun sendCommand(line: String): SendCommandType

    suspend fun sendCommandDirect(command: String)

    suspend fun print(message: StyledString)

    suspend fun debug(message: String)

    suspend fun scriptDebug(message: String)

    fun getStream(name: String): TextStream

    fun setMaxTypeAhead(value: Int)

    fun close()
}
