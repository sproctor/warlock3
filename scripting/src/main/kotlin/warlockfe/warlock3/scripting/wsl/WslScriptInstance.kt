package warlockfe.warlock3.scripting.wsl

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import warlockfe.warlock3.core.client.WarlockClient
import warlockfe.warlock3.core.prefs.HighlightRepository
import warlockfe.warlock3.core.prefs.VariableRepository
import warlockfe.warlock3.core.script.ScriptInstance
import warlockfe.warlock3.core.script.ScriptStatus
import warlockfe.warlock3.core.text.StyledString
import warlockfe.warlock3.core.text.WarlockStyle
import warlockfe.warlock3.core.util.parseArguments
import warlockfe.warlock3.core.util.toCaseInsensitiveMap
import warlockfe.warlock3.scripting.WarlockScriptEngineRegistry

class WslScriptInstance(
    override val name: String,
    override val id: Long,
    private val script: WslScript,
    private val variableRepository: VariableRepository,
    private val highlightRepository: HighlightRepository,
    private val scriptEngineRegistry: WarlockScriptEngineRegistry,
) : ScriptInstance {

    private val logger = KotlinLogging.logger {}
    override var status: ScriptStatus = ScriptStatus.NotStarted
        private set(newStatus) {
            field = newStatus
            scriptEngineRegistry.scriptStateChanged(this)
        }

    private lateinit var lines: List<WslLine>
    private val scope = CoroutineScope(Dispatchers.IO)
    private var job: Job? = null

    private val suspendedChannel = Channel<Unit>(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun start(client: WarlockClient, argumentString: String, onStop: () -> Unit) {
        val arguments = parseArguments(argumentString)
        status = ScriptStatus.Running

        job = scope.launch {
            try {
                client.sendCommandDirect("_state scripting on")
                lines = script.parse()
                val globalVariables = client.characterId.flatMapLatest { id ->
                    if (id != null) {
                        variableRepository.observeCharacterVariables(id).map { variables ->
                            variables.associate { it.name to it.value }
                                .toCaseInsensitiveMap()
                        }
                    } else {
                        flow {
                            emptyMap<String, String>()
                        }
                    }
                }
                    .stateIn(scope = scope)
                val context = WslContext(
                    client = client,
                    lines = lines,
                    scriptInstance = this@WslScriptInstance,
                    scope = scope,
                    globalVariables = globalVariables,
                    variableRepository = variableRepository,
                    highlightRepository = highlightRepository,
                )
                context.setScriptVariable("0", WslString(argumentString))
                arguments.forEachIndexed { index, arg ->
                    context.setScriptVariable((index + 1).toString(), WslString(arg))
                }
                while (status == ScriptStatus.Running || status == ScriptStatus.Suspended) {
                    val line = context.getNextLine()
                    if (line == null) {
                        status = ScriptStatus.Stopped
                        client.print(StyledString("Script \"$name\" ended"))
                        break
                    }
                    waitWhenSuspended()
                    line.statement.execute(context)
                }
            } catch (e: WslParseException) {
                status = ScriptStatus.Stopped
                client.print(StyledString(text = e.reason, styles = listOf(WarlockStyle.Error)))
            } catch (e: WslRuntimeException) {
                status = ScriptStatus.Stopped
                client.print(StyledString(text = "Script error: ${e.reason}", styles = listOf(WarlockStyle.Error)))
            } finally {
                try {
                    withContext(NonCancellable) {
                        client.sendCommandDirect("_state scripting off")
                        onStop()
                        scope.cancel()
                    }
                } catch (e: Throwable) {
                    logger.error(e) { "Problem terminating script: ${e.message}" }
                }
            }
        }
    }

    override fun stop() {
        status = ScriptStatus.Stopped
        job?.cancel()
    }

    override fun suspend() {
        status = ScriptStatus.Suspended
    }

    override fun resume() {
        status = ScriptStatus.Running
        suspendedChannel.trySend(Unit)
    }

    suspend fun waitWhenSuspended() {
        while (status == ScriptStatus.Suspended) {
            suspendedChannel.receive()
        }
    }
}