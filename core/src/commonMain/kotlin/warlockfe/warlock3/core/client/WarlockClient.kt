package warlockfe.warlock3.core.client

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import warlockfe.warlock3.core.text.StyledString
import warlockfe.warlock3.core.window.TextStream

interface WarlockClient {

    val eventFlow: SharedFlow<ClientEvent>

    val roundTime: StateFlow<Long?>
    val castTime: StateFlow<Long?>
    val gameName: StateFlow<String?>
    val characterName: StateFlow<String?>
    val leftHand: StateFlow<String?>
    val rightHand: StateFlow<String?>
    val spellHand: StateFlow<String?>

    val indicators: StateFlow<Set<String>>

    val menuData: StateFlow<WarlockMenuData>

    val characterId: StateFlow<String?>

    val time: Long

    val disconnected: StateFlow<Boolean>

    suspend fun connect(key: String)

    fun disconnect()

    suspend fun sendCommand(line: String): SendCommandType

    suspend fun sendCommandDirect(command: String)

    suspend fun print(message: StyledString)

    suspend fun debug(message: String)

    suspend fun scriptDebug(message: String)

    suspend fun getStream(name: String): TextStream

    fun getComponents(): Map<String, StyledString>

    fun getComponent(name: String): StyledString?

    fun setMaxTypeAhead(value: Int)

    fun close()
}
