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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import java.io.File
import kotlin.math.max

class GameViewModel(
    val client: StormfrontClient
) {
    private val scope = CoroutineScope(Dispatchers.IO)

    val properties: StateFlow<Map<String, String>> = client.properties

    private val currentTime: Flow<Int> = flow {
        while (true) {
            val time = client.time
            emit((time / 1000L).toInt())
            val nextSecond = 1000L - (time % 1000)
            delay(max(10L, nextSecond))
        }
    }

    val roundTime = combine(currentTime, properties) { currentTime, properties ->
        val roundEnd = properties["roundtime"]?.toIntOrNull() ?: 0
        max(0, roundEnd - currentTime)
    }
        .stateIn(scope = scope, started = SharingStarted.Eagerly, initialValue = 0)

    val castTime = combine(currentTime, properties) { currentTime, properties ->
        val roundEnd = properties["casttime"]?.toIntOrNull() ?: 0
        max(0, roundEnd - currentTime)
    }
        .stateIn(scope = scope, started = SharingStarted.Eagerly, initialValue = 0)

    private val _sendHistory = mutableStateOf<List<String>>(emptyList())
    val sendHistory: State<List<String>> = _sendHistory

    private val scriptInstances = mutableStateOf<List<ScriptInstance>>(emptyList())

    val windows = client.windows

    val openWindows = client.openWindows

    fun send(line: String) {
        _sendHistory.value = listOf(line) + _sendHistory.value
        if (line.startsWith(".")) {
            val splitCommand = line.drop(1).split(" ", "\t", limit = 2)
            val scriptName = splitCommand.firstOrNull() ?: ""
            val args = splitCommand.getOrNull(1) ?: ""
            val scriptDir = System.getProperty("user.home") + "/.warlock3/scripts"
            val file = File("$scriptDir/$scriptName.wsl")
            if (file.exists()) {
                client.print(StyledString("File exists"))
                val script = WslScript(name = scriptName, file = file)
                val scriptInstance = WslScriptInstance(name = scriptName, script = script)
                scriptInstance.start(client = client, argumentString = args)
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
