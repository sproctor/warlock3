package warlockfe.warlock3.scripting

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import warlockfe.warlock3.core.client.SendCommandType
import warlockfe.warlock3.core.client.WarlockClient
import warlockfe.warlock3.core.script.ScriptData
import warlockfe.warlock3.core.script.ScriptInstance
import warlockfe.warlock3.core.script.ScriptLaunchResult
import warlockfe.warlock3.core.script.ScriptManager
import warlockfe.warlock3.core.script.ScriptStatus
import warlockfe.warlock3.core.script.WarlockScriptEngineRepository
import warlockfe.warlock3.core.text.StyledString
import warlockfe.warlock3.core.text.WarlockStyle
import warlockfe.warlock3.core.util.splitFirstWord

class ScriptManagerImpl(
    private val fileSystem: FileSystem,
    private val scriptEngineRepository: WarlockScriptEngineRepository,
    private val externalScope: CoroutineScope,
) : ScriptManager {
    private val _runningScripts = MutableStateFlow<PersistentMap<Long, ScriptData>>(persistentMapOf())
    override val runningScripts = _runningScripts.asStateFlow()

    override suspend fun startScript(
        client: WarlockClient,
        command: String,
        commandHandler: suspend (String) -> SendCommandType
    ) {
        val (name, argString) = command.splitFirstWord()

        val result = scriptEngineRepository.getScript(name, client.characterId.value ?: "", this)
        when (result) {
            is ScriptLaunchResult.Success -> {
                startInstance(client, result.instance, argString, commandHandler)
            }
            is ScriptLaunchResult.Failure -> {
                client.print(StyledString(result.message, style = WarlockStyle.Error))
            }
        }
    }

    override suspend fun startScript(client: WarlockClient, file: Path, commandHandler: suspend (String) -> SendCommandType) {
        if (fileSystem.exists(file)) {
            val result = scriptEngineRepository.getScript(file, this)
            when (result) {
                is ScriptLaunchResult.Success -> {
                    startInstance(client, result.instance, null, commandHandler)
                }
                is ScriptLaunchResult.Failure -> {
                    client.print(StyledString(result.message, style = WarlockStyle.Error))
                }
            }
        }
    }

    private suspend fun startInstance(
        client: WarlockClient,
        instance: ScriptInstance,
        argString: String?,
        commandHandler: suspend (String) -> SendCommandType,
    ) {
        runningScripts.value.values.forEach { data ->
            if (instance.name == data.instance.name) {
                data.instance.stop()
            }
        }
        client.print(StyledString("Starting script: ${instance.name}", style = WarlockStyle.Echo))
        scriptStateChanged(instance)
        instance.start(
            client = client,
            argumentString = argString ?: "",
            onStop = {
                externalScope.launch {
                    _runningScripts.update { it.remove(instance.id) }
                    client.print(StyledString("Script has finished: ${instance.name}", style = WarlockStyle.Echo))
                }
            },
            commandHandler = commandHandler,
        )
    }

    override fun findScriptInstance(description: String): ScriptInstance? {
        val id = description.toLongOrNull()
        if (id != null) {
            runningScripts.value[id]?.let { return it.instance }
        }
        runningScripts.value.values.forEach { data ->
            if (data.instance.name.startsWith(description, true)) {
                return data.instance
            }
        }
        return null
    }

    override fun scriptStateChanged(instance: ScriptInstance) {
        _runningScripts.update { originalMap ->
            originalMap.toMutableMap().apply {
                if (instance.status == ScriptStatus.Stopped) {
                    remove(instance.id)
                } else {
                    this[instance.id] = ScriptData(instance.status, instance)
                }
            }
                .toPersistentMap()
        }
    }
}
