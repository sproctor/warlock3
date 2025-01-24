package warlockfe.warlock3.scripting.wsl

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.timeout
import warlockfe.warlock3.core.client.ClientNavEvent
import warlockfe.warlock3.core.client.ClientPromptEvent
import warlockfe.warlock3.core.client.ClientTextEvent
import warlockfe.warlock3.core.client.SendCommandType
import warlockfe.warlock3.core.client.WarlockClient
import warlockfe.warlock3.core.prefs.HighlightRepository
import warlockfe.warlock3.core.prefs.VariableRepository
import warlockfe.warlock3.core.prefs.models.Highlight
import warlockfe.warlock3.core.script.ScriptManager
import warlockfe.warlock3.core.script.ScriptStatus
import warlockfe.warlock3.core.text.StyleDefinition
import warlockfe.warlock3.core.text.StyledString
import warlockfe.warlock3.core.text.WarlockStyle
import warlockfe.warlock3.core.util.CaseInsensitiveMap
import warlockfe.warlock3.core.util.parseArguments
import warlockfe.warlock3.core.util.splitFirstWord
import warlockfe.warlock3.scripting.util.ScriptLoggingLevel
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.time.Duration.Companion.milliseconds

// TODO: generate constructor with a factory
class WslContext(
    private val client: WarlockClient,
    private val scriptManager: ScriptManager,
    val lines: List<WslLine>,
    val scriptInstance: WslScriptInstance,
    scope: CoroutineScope,
    private val globalVariables: StateFlow<Map<String, String>>,
    private val variableRepository: VariableRepository,
    private val highlightRepository: HighlightRepository,
    private val commandHandler: (String) -> SendCommandType,
) {

    private val scriptVariables = CaseInsensitiveMap(
        "components" to WslComponents(client),
        "monstercount" to WslMonsterCount(client),
        "properties" to WslProperties(client),
        "variables" to WslVariables(this),
    )

    private val matches = mutableListOf<WslMatch>()
    private val listeners = mutableListOf<Pair<String, suspend (String) -> Unit>>()

    private val frameStack = mutableListOf(
        WslFrame(0)
    )
    private val currentFrame: WslFrame
        get() = frameStack.last()

    private var loggingLevel = 20

    private var maxTypeAhead = 2
    private var typeAhead = AtomicInteger(0)

    private val navChannel = Channel<Unit>(0)
    private val promptChannel = Channel<Unit>(0)

    init {
        client.eventFlow
            .onEach { event ->
                when (event) {
                    is ClientPromptEvent -> {
                        typeAhead.getAndUpdate { max(0, it - 1) }
                        promptChannel.trySend(Unit)
                    }

                    is ClientTextEvent -> {
                        listeners.forEach { (_, action) ->
                            action(event.text)
                        }
                    }

                    ClientNavEvent -> {
                        navChannel.trySend(Unit)
                    }

                    else -> Unit
                }
            }
            .launchIn(scope)
    }

    suspend fun executeCommand(commandLine: String) {
        val lineNumber = currentFrame.lineNumber
        log(ScriptLoggingLevel.VERBOSE, "Line $lineNumber: $commandLine")

        val (commandName, args) = commandLine.splitFirstWord()
        val command = wslCommands[commandName.lowercase()]
            ?: throw WslRuntimeException("Invalid command \"$commandName\" on line $lineNumber")
        command(this, args ?: "")
    }

    private fun getGlobalVariable(name: String): String? {
        return globalVariables.value[name]
    }

    fun lookupVariable(name: String): WslValue? {
        currentFrame.lookupVariable(name)?.let { return it }
        scriptVariables[name]?.let { return it }
        return getGlobalVariable(name)?.let { WslString(it) }
    }

    fun hasVariable(name: String): Boolean {
        return scriptVariables.containsKey(name) || (getGlobalVariable(name) != null)
    }

    suspend fun setStoredVariable(name: String, value: String) {
        client.characterId.value?.let { variableRepository.put(it.lowercase(), name, value) }
        log(ScriptLoggingLevel.INFO, "SetVariable: $name=$value")
    }

    suspend fun deleteStoredVariable(name: String) {
        client.characterId.value?.let { variableRepository.delete(it.lowercase(), name) }
        log(ScriptLoggingLevel.INFO, "DeleteVariable: $name")
    }

    suspend fun setScriptVariable(name: String, value: WslValue) {
        scriptVariables += name to value
        log(ScriptLoggingLevel.DEBUG, "Set script variable \"$name\" to $value")
    }

    fun setScriptVariableRaw(name: String, value: WslValue) {
        scriptVariables += name to value
    }

    suspend fun deleteScriptVariable(name: String) {
        scriptVariables -= name
        log(ScriptLoggingLevel.DEBUG, "Deleted script variable: $name")
    }

    suspend fun setLocalVariable(name: String, value: WslValue) {
        currentFrame.setVariable(name, value)
        log(ScriptLoggingLevel.DEBUG, "Set local variable \"$name\" to $value")
    }

    suspend fun deleteLocalVariable(name: String) {
        currentFrame.deleteVariable(name)
        log(ScriptLoggingLevel.DEBUG, "Deleted local variable: $name")
    }

    fun getNextLine(): WslLine? {
        val currentLine = currentFrame.nextLine()
        if (currentLine >= lines.size) {
            return null
        }
        return lines[currentLine]
    }

    fun stop() {
        scriptInstance.stop()
    }

    suspend fun echo(message: String) {
        client.print(StyledString(message, WarlockStyle.Echo))
    }

    suspend fun log(level: ScriptLoggingLevel, message: String) {
        log(level.level, message)
    }

    suspend fun log(level: Int, message: String) {
        if (level >= loggingLevel) {
            client.scriptDebug(message)
        }
    }

    fun setLoggingLevel(level: Int) {
        loggingLevel = level
    }

    suspend fun putCommand(command: String) {
        waitForRoundTime()
        sendCommand(command)
    }

    suspend fun sendCommand(command: String) {
        while (typeAhead.get() >= maxTypeAhead) {
            waitForPrompt()
        }
        val result = commandHandler(command)
        if (result == SendCommandType.COMMAND) {
            typeAhead.incrementAndGet()
        }
        log(ScriptLoggingLevel.VERBOSE, "Sent: $command")
        if (result == SendCommandType.SCRIPT) {
            stop()
        }
    }

    suspend fun runCommand(scriptCommand: String) {
        scriptManager.startScript(client, scriptCommand, commandHandler)
    }

    suspend fun goto(label: String) {
        var index = lines.indexOfFirst { line ->
            line.labels.any { it.equals(other = label, ignoreCase = true) }
        }
        if (index == -1) {
            index = lines.indexOfFirst { line ->
                line.labels.any { it.equals(other = "labelError", ignoreCase = true) }
            }
            if (index != -1) {
                log(ScriptLoggingLevel.DEBUG, "goto \"labelError\" line $index")
            }
        } else {
            log(ScriptLoggingLevel.DEBUG, "goto \"$label\" line $index")
        }
        if (index == -1) {
            throw WslRuntimeException("Could not find label \"$label\".")
        }
        currentFrame.goto(index)
    }

    fun gosub(label: String, args: String) {
        val lineIndex = lines.indexOfFirst { line ->
            line.labels.any { it.equals(other = label, ignoreCase = true) }
        }
        if (lineIndex == -1) {
            throw WslRuntimeException("Could not find label \"$label\".")
        }
        frameStack.add(WslFrame(lineIndex))
        val parsedArgs = parseArguments(args)
        currentFrame.setVariable(
            name = "args",
            value = WslMap(parsedArgs.mapIndexed { i, s -> i.toString() to WslString(s) }.toMap()),
        )
    }

    fun gosubReturn() {
        if (frameStack.size > 1) {
            frameStack.removeAt(frameStack.lastIndex)
        } else {
            throw WslRuntimeException("Return called outside of a subroutine")
        }
    }

    suspend fun waitForNav() {
        log(ScriptLoggingLevel.DEBUG, "waiting for next room")
        navChannel.receive()
    }

    suspend fun waitForPrompt() {
        log(ScriptLoggingLevel.DEBUG, "waiting for next prompt")
        promptChannel.receive()
        scriptInstance.waitWhenSuspended()
    }

    suspend fun waitForText(text: String, ignoreCase: Boolean) {
        log(ScriptLoggingLevel.DEBUG, "waiting for text: $text")
        client.eventFlow.first {
            it is ClientTextEvent && it.text.contains(other = text, ignoreCase = ignoreCase)
        }
    }

    suspend fun waitForRegex(regex: Regex) {
        log(ScriptLoggingLevel.DEBUG, "waiting for regex: ${regex.pattern}")
        client.eventFlow.first {
            it is ClientTextEvent && regex.containsMatchIn(it.text)
        }
    }

    fun addMatch(match: WslMatch) {
        matches += match
    }

    @OptIn(FlowPreview::class)
    suspend fun matchWait(timeout: Float?) {
        if (matches.isEmpty()) {
            throw WslRuntimeException("matchWait called with no matches")
        }

        client.eventFlow
            .let {
                if (timeout != null) {
                    val millis = (timeout * 1000).toLong()
                    it.timeout(millis.milliseconds)
                } else {
                    it
                }
            }
            .first { event ->
                if (event is ClientTextEvent && scriptInstance.status == ScriptStatus.Running) {
                    matches.firstOrNull { match ->
                        match.match(event.text)?.let { text ->
                            log(ScriptLoggingLevel.DEBUG, "matched \"${match.label}\": $text")
                            goto(match.label)
                            return@first true
                        }
                        false
                    }
                }
                false
            }

        matches.clear()
    }

    suspend fun waitForRoundTime() {
        log(ScriptLoggingLevel.DEBUG, "waiting for round time")
        while (true) {
            val roundEnd = client.properties.value["roundtime"]?.toLongOrNull()?.let { it * 1000L } ?: 0
            val currentTime = client.time
            if (roundEnd <= currentTime) {
                break
            }
            val duration = roundEnd - currentTime
            log(ScriptLoggingLevel.VERBOSE, "wait duration: ${duration}ms")
            delay(duration)
            scriptInstance.waitWhenSuspended()
        }
        log(ScriptLoggingLevel.VERBOSE, "done waiting for round time")
    }

    suspend fun addHighlight(
        pattern: String,
        style: StyleDefinition,
        matchPartialWord: Boolean,
        ignoreCase: Boolean,
        isRegex: Boolean,
    ) {
        client.characterId.value?.lowercase()?.let { characterId ->
            highlightRepository.save(
                characterId,
                Highlight(
                    id = UUID.randomUUID(),
                    pattern = pattern,
                    styles = mapOf(0 to style),
                    matchPartialWord = matchPartialWord,
                    ignoreCase = ignoreCase,
                    isRegex = isRegex,
                )
            )
        }
    }

    suspend fun deleteHighlight(pattern: String) {
        client.characterId.value?.lowercase()?.let { characterId ->
            highlightRepository.deleteByPattern(characterId, pattern)
        }
    }

    fun addListener(name: String, action: suspend (String) -> Unit) {
        listeners.add(name to action)
    }

    fun removeListener(name: String) {
        listeners.removeIf { (varName, _) ->
            varName == name
        }
    }

    fun clearListeners() {
        listeners.clear()
    }

    fun setTypeahead(value: Int) {
        maxTypeAhead = value
    }
}
