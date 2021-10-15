package cc.warlock.warlock3.app.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import cc.warlock.warlock3.core.ScriptInstance
import cc.warlock.warlock3.core.StyledString
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

    private val _sendHistory = mutableStateOf<List<String>>(emptyList())
    val sendHistory: State<List<String>> = _sendHistory

    private val scope = CoroutineScope(Dispatchers.IO)
    private val scriptInstances = mutableStateOf<List<ScriptInstance>>(emptyList())

    val windows = client.windows

    val openWindows = client.openWindows

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
                scriptInstances.value += scriptInstance
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

    fun stopScripts() {
        val count = scriptInstances.value.size
        scriptInstances.value.forEach { scriptInstance ->
            scriptInstance.stop()
        }
        scriptInstances.value = emptyList()
        client.print(StyledString("Stopped $count script(s)"))
    }
}
