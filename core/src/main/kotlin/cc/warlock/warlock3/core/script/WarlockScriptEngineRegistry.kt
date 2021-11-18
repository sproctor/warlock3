package cc.warlock.warlock3.core.script

import cc.warlock.warlock3.core.client.WarlockClient
import cc.warlock.warlock3.core.highlights.HighlightRegistry
import cc.warlock.warlock3.core.script.js.JsEngine
import cc.warlock.warlock3.core.script.wsl.WslEngine
import cc.warlock.warlock3.core.script.wsl.splitFirstWord
import cc.warlock.warlock3.core.text.StyledString
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import javax.script.ScriptEngineManager

class WarlockScriptEngineRegistry(
    highlightRegistry: HighlightRegistry,
    variableRegistry: VariableRegistry,
) {

    private val _runningScripts = MutableStateFlow<List<ScriptInstance>>(emptyList())
    val runningScripts = _runningScripts

    private val jvmScriptManager = ScriptEngineManager()

    private val engines = listOf(
        WslEngine(highlightRegistry = highlightRegistry, variableRegistry = variableRegistry),
        JsEngine(jvmScriptManager)
    )

    suspend fun startScript(client: WarlockClient, command: String) {
        val (name, argString) = command.splitFirstWord()

        val instance = findInstance(name)
        if (instance != null) {
            client.print(StyledString("Starting script: $name"))
            instance.start(client = client, argumentString = argString ?: "")
            _runningScripts.value += instance
        } else {
            client.print(StyledString("Could not find a script with that name"))
        }
    }

    private fun findInstance(name: String): ScriptInstance? {
        val scriptDir = System.getProperty("user.home") + "/.warlock3/scripts"
        for (engine in engines) {
            for (extension in engine.extensions) {
                val file = File("$scriptDir/$name.$extension")
                if (file.exists()) {
                    return engine.createInstance(name, file)
                }
            }
        }
        return null
    }
}