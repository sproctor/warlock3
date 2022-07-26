package cc.warlock.warlock3.core.script.wsl

import cc.warlock.warlock3.core.client.ClientNavEvent
import cc.warlock.warlock3.core.client.ClientPromptEvent
import cc.warlock.warlock3.core.client.ClientTextEvent
import cc.warlock.warlock3.core.client.WarlockClient
import cc.warlock.warlock3.core.prefs.models.Highlight
import cc.warlock.warlock3.core.prefs.HighlightRepository
import cc.warlock.warlock3.core.prefs.VariableRepository
import cc.warlock.warlock3.core.script.ScriptStatus
import cc.warlock.warlock3.core.text.StyleDefinition
import cc.warlock.warlock3.core.text.StyledString
import cc.warlock.warlock3.core.text.WarlockStyle
import cc.warlock.warlock3.core.util.CaseInsensitiveMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

class WslContext(
    private val client: WarlockClient,
    val lines: List<WslLine>,
    val scriptInstance: WslScriptInstance,
    private val scope: CoroutineScope,
    private val globalVariables: StateFlow<Map<String, String>>,
    private val variableRepository: VariableRepository,
    private val highlightRepository: HighlightRepository,
) : AutoCloseable {

    private val scriptVariables = CaseInsensitiveMap(
        "components" to WslComponents(client),
        "monstercount" to WslMonsterCount(client),
        "properties" to WslProperties(client),
        "variables" to WslVariables(this),
    )

    private val matches = mutableListOf<WslMatch>()
    private val listeners = mutableMapOf<String, (String) -> Unit>()

    private val frameStack = mutableListOf(
        WslFrame(0)
    )
    private val currentFrame: WslFrame
        get() = frameStack.last()

    private var loggingLevel = 30

    var typeAhead = 0

    private val mutex = Mutex()

    private val navChannel = Channel<Unit>(0)
    private val promptChannel = Channel<Unit>(0)

    init {
        client.eventFlow
            .onEach { event ->
                when (event) {
                    is ClientPromptEvent -> {
                        mutex.withLock {
                            if (typeAhead > 0)
                                typeAhead--
                            promptChannel.trySend(Unit)
                        }
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
        log(5, "Line $lineNumber: $commandLine")

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
        log(10, "Setting stored variable \"$name\" to $value")
        client.characterId.value?.let { variableRepository.put(it.lowercase(), name, value) }
    }

    suspend fun deleteStoredVariable(name: String) {
        log(10, "Deleting stored variable: $name")
        client.characterId.value?.let { variableRepository.delete(it.lowercase(), name) }
    }

    fun setScriptVariable(name: String, value: WslValue) {
        log(10, "Setting script variable \"$name\" to $value")
        scriptVariables += name to value
    }

    fun deleteScriptVariable(name: String) {
        log(10, "Deleting script variable: $name")
        scriptVariables -= name
    }

    fun setLocalVariable(name: String, value: WslValue) {
        log(10, "Setting local variable \"$name\" to $value")
        currentFrame.setVariable(name, value)
    }

    fun deleteLocalVariable(name: String) {
        log(10, "Deleting local variable: $name")
        currentFrame.deleteVariable(name)
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
        client.print(StyledString(message, listOf(WarlockStyle.Echo)))
    }

    fun log(level: Int, message: String) {
        if (level >= loggingLevel) {
            scope.launch {
                client.debug(message)
            }
        }
    }

    fun setLoggingLevel(level: Int) {
        loggingLevel = level
    }

    suspend fun putCommand(command: String) {
        waitForRoundTime()
        mutex.withLock {
            typeAhead++
        }
        if (typeAhead > client.maxTypeAhead) {
            waitForPrompt()
        }
        log(5, "sending command: $command")
        client.sendCommand(command)
    }

    suspend fun sendCommand(command: String) {
        mutex.withLock {
            typeAhead++
        }
        log(5, "sending command: $command")
        client.sendCommand(command)
    }

    fun goto(label: String) {
        var index = lines.indexOfFirst { line ->
            line.labels.any { it.equals(other = label, ignoreCase = true) }
        }
        if (index == -1) {
            index = lines.indexOfFirst { line ->
                line.labels.any { it.equals(other = "labelError", ignoreCase = true) }
            }
            if (index != -1) {
                log(5, "goto \"labelError\" line $index")
            }
        } else {
            log(5, "goto \"$label\" line $index")
        }
        if (index == -1) {
            throw WslRuntimeException("Could not find label \"$label\".")
        }
        currentFrame.goto(index)
    }

    fun gosub(label: String, args: List<String>) {
        val lineIndex = lines.indexOfFirst { line ->
            line.labels.any { it.equals(other = label, ignoreCase = true) }
        }
        if (lineIndex == -1) {
            throw WslRuntimeException("Could not find label \"$label\".")
        }
        frameStack.add(WslFrame(lineIndex))
        args.forEachIndexed { i, s ->
            currentFrame.setVariable("arg${i + 1}", WslString(s))
        }
        currentFrame.setVariable(
            name = "args",
            value = WslMap(args.mapIndexed { i, s -> i.toString() to WslString(s) }.toMap()),
        )
    }

    fun gosubReturn() {
        if (frameStack.size > 1) {
            frameStack.removeLast()
        } else {
            throw WslRuntimeException("Return called outside of a subroutine")
        }
    }

    suspend fun waitForNav() {
        log(5, "waiting for next room")
        navChannel.receive()
    }

    suspend fun waitForPrompt() {
        log(5, "waiting for next prompt")
        promptChannel.receive()
        scriptInstance.waitWhenSuspended()
    }

    suspend fun waitForText(text: String, ignoreCase: Boolean) {
        log(5, "waiting for text: $text")
        client.eventFlow.first {
            it is ClientTextEvent && it.text.contains(other = text, ignoreCase = ignoreCase)
        }
    }

    suspend fun waitForRegex(regex: Regex) {
        log(5, "waiting for regex: ${regex.pattern}")
        client.eventFlow.first {
            it is ClientTextEvent && regex.containsMatchIn(it.text)
        }
    }

    fun addMatch(match: WslMatch) {
        matches += match
    }

    suspend fun matchWait() {
        if (matches.isEmpty()) {
            throw WslRuntimeException("matchWait called with no matches")
        }

        client.eventFlow.first { event ->
            if (event is ClientTextEvent && scriptInstance.status == ScriptStatus.Running) {
                matches.firstOrNull { match ->
                    match.match(event.text)?.let { text ->
                        log(5, "matched \"${match.label}\": $text")
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
        log(5, "waiting for round time")
        while (true) {
            val roundEnd = client.properties.value["roundtime"]?.toLongOrNull()?.let { it * 1000L } ?: 0
            val currentTime = client.time
            if (roundEnd <= currentTime) {
                break
            }
            val duration = roundEnd - currentTime
            log(0, "wait duration: ${duration}ms")
            delay(duration)
            scriptInstance.waitWhenSuspended()
        }
        log(5, "done waiting for round time")
    }

    override fun close() {
        scope.cancel()
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

    fun addListener(name: String, action: (String) -> Unit) {
        listeners[name] = action
    }

    fun removeListener(name: String) {
        listeners.remove(name)
    }
}
