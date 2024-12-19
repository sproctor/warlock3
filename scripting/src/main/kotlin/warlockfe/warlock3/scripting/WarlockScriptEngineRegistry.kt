package warlockfe.warlock3.scripting

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import warlockfe.warlock3.core.client.WarlockClient
import warlockfe.warlock3.core.prefs.HighlightRepository
import warlockfe.warlock3.core.prefs.ScriptDirRepository
import warlockfe.warlock3.core.prefs.VariableRepository
import warlockfe.warlock3.core.script.ScriptInfo
import warlockfe.warlock3.core.script.ScriptInstance
import warlockfe.warlock3.core.script.ScriptManager
import warlockfe.warlock3.core.script.ScriptStatus
import warlockfe.warlock3.core.text.StyledString
import warlockfe.warlock3.core.text.WarlockStyle
import warlockfe.warlock3.scripting.js.JsEngine
import warlockfe.warlock3.scripting.wsl.WslEngine
import warlockfe.warlock3.scripting.wsl.splitFirstWord
import java.io.File

class WarlockScriptEngineRegistry(
    highlightRepository: HighlightRepository,
    variableRepository: VariableRepository,
    private val scriptDirRepository: ScriptDirRepository,
    private val externalScope: CoroutineScope,
) : ScriptManager {

    private val _scriptInfo = MutableStateFlow<Map<Long, ScriptInfo>>(emptyMap())
    override val scriptInfo = _scriptInfo.asStateFlow()

    override val runningScripts = mutableSetOf<ScriptInstance>()

    private var nextId = 0L

    private val engines = listOf(
        WslEngine(
            highlightRepository = highlightRepository,
            variableRepository = variableRepository,
            scriptEngineRegistry = this
        ),
        JsEngine(variableRepository, this)
    )

    override suspend fun startScript(client: WarlockClient, command: String) {
        val (name, argString) = command.splitFirstWord()

        val instance = createInstance(name, client.characterId.value ?: "")
        if (instance != null) {
            startInstance(client, instance, argString)
        } else {
            client.print(StyledString("Could not find a script with that name", style = WarlockStyle.Error))
        }
    }

    override suspend fun startScript(client: WarlockClient, file: File) {
        if (file.exists()) {
            val engine = getEngineForExtension(file.extension)
            if (engine == null) {
                client.print(StyledString("That extension is not supported"))
            } else {
                val instance = engine.createInstance(file.name, nextId++, file)
                startInstance(client, instance, null)
            }
        }
    }

    private suspend fun startInstance(client: WarlockClient, instance: ScriptInstance, argString: String?) {
        runningScripts.forEach { runningInstance ->
            if (instance.name == runningInstance.name) {
                runningInstance.stop()
            }
        }
        client.print(StyledString("Starting script: ${instance.name}", style = WarlockStyle.Echo))
        runningScripts += instance
        scriptStateChanged(instance)
        instance.start(client = client, argumentString = argString ?: "") {
            externalScope.launch {
                runningScripts -= instance
                _scriptInfo.update { it.minus(instance.id) }
                client.print(StyledString("Script has finished: ${instance.name}", style = WarlockStyle.Echo))
            }
        }
    }

    private suspend fun createInstance(name: String, characterId: String): ScriptInstance? {
        for (engine in engines) {
            for (extension in engine.extensions) {
                for (scriptDir in scriptDirRepository.getMappedScriptDirs(characterId)) {
                    val file = File("$scriptDir/$name.$extension")
                    if (file.exists()) {
                        return engine.createInstance(name, nextId++, file)
                    }
                }
            }
        }
        return null
    }

    private fun getEngineForExtension(extension: String): WarlockScriptEngine? {
        for (engine in engines) {
            for (validExtension in engine.extensions) {
                if (extension == validExtension) {
                    return engine
                }
            }
        }
        return null
    }

    override fun supportsExtension(extension: String): Boolean {
        return getEngineForExtension(extension) != null
    }

    override fun findScriptInstance(description: String): ScriptInstance? {
        val id = description.toLongOrNull()
        runningScripts.forEach { instance ->
            if (instance.name.startsWith(description, true) || instance.id == id) {
                return instance
            }
        }
        return null
    }

    fun scriptStateChanged(instance: ScriptInstance) {
        if (instance.status == ScriptStatus.Stopped) {
            _scriptInfo.update { it - instance.id }
        } else {
            _scriptInfo.update { it.plus(instance.id to ScriptInfo(instance.name, instance.status)) }
        }
    }
}
