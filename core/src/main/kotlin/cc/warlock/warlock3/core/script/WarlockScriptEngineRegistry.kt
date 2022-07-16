package cc.warlock.warlock3.core.script

import cc.warlock.warlock3.core.client.WarlockClient
import cc.warlock.warlock3.core.prefs.HighlightRepository
import cc.warlock.warlock3.core.prefs.ScriptDirRepository
import cc.warlock.warlock3.core.prefs.VariableRepository
import cc.warlock.warlock3.core.script.js.JsEngine
import cc.warlock.warlock3.core.script.wsl.WslEngine
import cc.warlock.warlock3.core.script.wsl.splitFirstWord
import cc.warlock.warlock3.core.text.StyledString
import cc.warlock.warlock3.core.text.WarlockStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import java.io.File

class WarlockScriptEngineRegistry(
    highlightRepository: HighlightRepository,
    variableRepository: VariableRepository,
    private val scriptDirRepository: ScriptDirRepository,
) {

    private val _runningScripts = MutableStateFlow<List<ScriptInstance>>(emptyList())
    val runningScripts = _runningScripts

    private val engines = listOf(
        WslEngine(highlightRepository = highlightRepository, variableRepository = variableRepository),
        JsEngine(variableRepository, this)
    )

    suspend fun startScript(client: WarlockClient, command: String) {
        val (name, argString) = command.splitFirstWord()

        val instance = findInstance(name, client.characterId.value ?: "")
        if (instance != null) {
            client.print(StyledString("Starting script: $name", style = WarlockStyle.Echo))
            _runningScripts.value += instance
            instance.start(client = client, argumentString = argString ?: "") {
                _runningScripts.value -= instance
                runBlocking {
                    client.print(StyledString("Script has finished: $name", style = WarlockStyle.Echo))
                }
            }
        } else {
            client.print(StyledString("Could not find a script with that name", style = WarlockStyle.Error))
        }
    }

    private suspend fun findInstance(name: String, characterId: String): ScriptInstance? {
        for (engine in engines) {
            for (extension in engine.extensions) {
                for (scriptDir in scriptDirRepository.getMappedScriptDirs(characterId)) {
                    val file = File("$scriptDir/$name.$extension")
                    if (file.exists()) {
                        return engine.createInstance(name, file)
                    }
                }
            }
        }
        return null
    }
}