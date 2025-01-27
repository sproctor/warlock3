package warlockfe.warlock3.scripting

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import warlockfe.warlock3.core.client.SendCommandType
import warlockfe.warlock3.core.client.WarlockClient
import warlockfe.warlock3.core.script.ScriptInstance
import warlockfe.warlock3.core.script.ScriptManager
import warlockfe.warlock3.core.script.ScriptStatus
import warlockfe.warlock3.core.script.WarlockScriptEngineRepository
import warlockfe.warlock3.core.text.StyledString
import warlockfe.warlock3.core.text.WarlockStyle
import warlockfe.warlock3.core.util.splitFirstWord
import java.io.File

class ScriptManagerImpl(
    private val scriptEngineRepository: WarlockScriptEngineRepository,
    private val externalScope: CoroutineScope,
) : ScriptManager {
    private val _runningScripts = MutableStateFlow<Map<Long, ScriptInstance>>(emptyMap())
    override val runningScripts = _runningScripts.asStateFlow()

    private var nextId = 0L

    override suspend fun startScript(
        client: WarlockClient,
        command: String,
        commandHandler: suspend (String) -> SendCommandType
    ) {
        val (name, argString) = command.splitFirstWord()

        val instance = scriptEngineRepository.getScript(name, client.characterId.value ?: "", this)
        if (instance != null) {
            startInstance(client, instance, argString, commandHandler)
        } else {
            client.print(StyledString("Could not find a script with that name", style = WarlockStyle.Error))
        }
    }

    override suspend fun startScript(client: WarlockClient, file: File, commandHandler: suspend (String) -> SendCommandType) {
        if (file.exists()) {
            val instance = scriptEngineRepository.getScript(file, this)
            if (instance != null) {
                startInstance(client, instance, null, commandHandler)
            } else {
                client.print(StyledString("Could not find a script with that name", style = WarlockStyle.Error))
            }
        }
    }

    private suspend fun startInstance(
        client: WarlockClient,
        instance: ScriptInstance,
        argString: String?,
        commandHandler: suspend (String) -> SendCommandType,
    ) {
        val id = nextId++
        runningScripts.value.values.forEach { runningInstance ->
            if (instance.name == runningInstance.name) {
                runningInstance.stop()
            }
        }
        client.print(StyledString("Starting script: ${instance.name}", style = WarlockStyle.Echo))
        _runningScripts.update { it + (id to instance) }
        scriptStateChanged(instance)
        instance.start(
            client = client,
            argumentString = argString ?: "",
            onStop = {
                externalScope.launch {
                    _runningScripts.update { it - id }
                    client.print(StyledString("Script has finished: ${instance.name}", style = WarlockStyle.Echo))
                }
            },
            commandHandler = commandHandler,
        )
    }

    override fun findScriptInstance(description: String): ScriptInstance? {
        val id = description.toLongOrNull()
        if (id != null) {
            runningScripts.value[id]?.let { return it }
        }
        runningScripts.value.values.forEach { instance ->
            if (instance.name.startsWith(description, true)) {
                return instance
            }
        }
        return null
    }

    override fun scriptStateChanged(instance: ScriptInstance) {
        if (instance.status == ScriptStatus.Stopped) {
            _runningScripts.update { originalMap ->
                originalMap.filter { it.value == instance }
            }
        }
    }
}
