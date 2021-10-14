package cc.warlock.warlock3.app.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import cc.warlock.warlock3.core.*
import cc.warlock.warlock3.core.wsl.WslScript
import cc.warlock.warlock3.core.wsl.WslScriptInstance
import cc.warlock.warlock3.stormfront.network.StormfrontClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.io.File

class GameViewModel(
    val client: StormfrontClient
) {
    private val _properties = mutableStateOf<Map<String, String>>(emptyMap())
    val properties: State<Map<String, String>> = _properties

    val components = client.components

    private val _sendHistory = mutableStateOf<List<String>>(emptyList())
    val sendHistory: State<List<String>> = _sendHistory

    private val scope = CoroutineScope(Dispatchers.IO)
    private val scriptInstances = mutableStateOf<List<ScriptInstance>>(emptyList())

    val windows = client.windows

    val openWindows = client.openWindows

    val defaultBackgroundColor = mutableStateOf(Color.DarkGray)
    val defaultTextColor = mutableStateOf(Color.White)

    fun send(line: String) {
        _sendHistory.value = listOf(line) + _sendHistory.value
        if (line.startsWith(".")) {
            val scriptName = line.drop(1)
            val scriptDir = System.getProperty("user.home") + "/.warlock3/scripts"
            val file = File("$scriptDir/$scriptName.wsl")
            if (file.exists()) {
                client.print(StyledString("File exists"))
                val script = WslScript(name = scriptName, file = file)
                val scriptInstance = WslScriptInstance(name = scriptName, script = script)
                scriptInstance.start(client, emptyList())
            } else {
                client.print(StyledString("Could not find a script with that name"))
            }
        } else {
            client.sendCommand(line)
        }
    }

    fun showWindow(name: String) {
        client.showWindow(name)
    }

    fun hideWindow(name: String) {
        client.hideWindow(name)
    }
}
