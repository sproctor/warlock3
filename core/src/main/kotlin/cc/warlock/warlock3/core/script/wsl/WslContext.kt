package cc.warlock.warlock3.core.script.wsl

import cc.warlock.warlock3.core.client.ClientNavEvent
import cc.warlock.warlock3.core.client.ClientPromptEvent
import cc.warlock.warlock3.core.client.ClientTextEvent
import cc.warlock.warlock3.core.client.WarlockClient
import cc.warlock.warlock3.core.highlights.Highlight
import cc.warlock.warlock3.core.highlights.HighlightRegistry
import cc.warlock.warlock3.core.script.VariableRegistry
import cc.warlock.warlock3.core.text.StyledString
import cc.warlock.warlock3.core.text.StyleDefinition
import cc.warlock.warlock3.core.util.CaseInsensitiveMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.locks.ReentrantLock

class WslContext(
    private val client: WarlockClient,
    val lines: List<WslLine>,
    val scriptInstance: WslScriptInstance,
    private val variableRegistry: VariableRegistry,
    private val highlightRegistry: HighlightRegistry,
) : AutoCloseable {
    private val scope = CoroutineScope(Dispatchers.IO)

    private val scriptVariables = CaseInsensitiveMap(
        "components" to WslComponents(client),
        "properties" to WslProperties(client),
        "variables" to WslVariables(this),
    )

    private val matches = mutableListOf<ScriptMatch>()
    private val listeners = mutableMapOf<String, (String) -> Unit>()

    private val frameStack = mutableListOf(
        WslFrame(0)
    )
    private val currentFrame: WslFrame
        get() = frameStack.last()

    private var loggingLevel = 30

    var typeAhead = 0

    private val mutex = Mutex()

    private val navLock = ReentrantLock()
    private val navCondition = navLock.newCondition()

    private val promptLock = ReentrantLock()
    private val promptCondition = promptLock.newCondition()

    init {
        client.eventFlow
            .onEach { event ->
                when (event) {
                    is ClientPromptEvent -> {
                        mutex.withLock {
                            if (typeAhead > 0)
                                typeAhead--
                        }
                        promptCondition.signalAll()
                    }
                    is ClientTextEvent -> {
                        listeners.forEach { (_, action) ->
                            action(event.text)
                        }
                    }
                    ClientNavEvent -> {
                        navCondition.signalAll()
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
        val characterId = client.characterId.value?.lowercase() ?: return null
        val clientVariables = variableRegistry.variables.value[characterId]
        clientVariables?.forEach { (key, value) ->
            if (key.equals(name, ignoreCase = true)) {
                return value
            }
        }
        return null
    }

    fun lookupVariable(name: String): WslValue? {
        frameStack.reversed().forEach { frame ->
            val value = frame.lookupVariable(name)
            if (value != null)
                return value
        }
        val scriptValue = scriptVariables[name]
        if (scriptValue != null)
            return scriptValue
        return getGlobalVariable(name)?.let { WslString(it) }
    }

    fun hasVariable(name: String): Boolean {
        return scriptVariables.containsKey(name) || (getGlobalVariable(name) != null)
    }

    fun setStoredVariable(name: String, value: String) {
        log(10, "Setting stored variable \"$name\" to $value")
        client.characterId.value?.let { variableRegistry.setVariable(it.lowercase(), name, value) }
    }

    fun deleteStoredVariable(name: String) {
        log(10, "Deleting stored variable: $name")
        client.characterId.value?.let { variableRegistry.deleteVariable(it.lowercase(), name) }
    }

    fun setScriptVariable(name: String, value: WslValue) {
        log(10, "Setting script variable \"$name\" to $value")
        scriptVariables += name to value
    }

    fun deleteScriptVariable(name: String) {
        log(10, "Deleting script variable: $name")
        scriptVariables -= name
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

    fun echo(message: String) {
        client.print(StyledString(message))
    }

    fun log(level: Int, message: String) {
        if (level >= loggingLevel) {
            client.print(StyledString(message))
        }
    }

    fun setLoggingLevel(level: Int) {
        loggingLevel = level
    }

    suspend fun putCommand(command: String) {
        log(5, "sending command: $command")
        waitForRoundTime()
        while (typeAhead >= client.maxTypeAhead) {
            waitForPrompt()
        }
        mutex.withLock {
            typeAhead++
        }
        client.sendCommand(command)
    }

    suspend fun sendCommand(command: String) {
        log(5, "sending command: $command")
        mutex.withLock {
            typeAhead++
        }
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
        val index = lines.indexOfFirst { line ->
            line.labels.any { it.equals(other = label, ignoreCase = true) }
        }
        if (index == -1) {
            throw WslRuntimeException("Could not find label \"$label\".")
        }
        frameStack.add(WslFrame(index))
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

    fun waitForNav() {
        log(5, "waiting for next room")
        navCondition.await()
    }

    fun waitForPrompt() {
        log(5, "waiting for next prompt")
        promptCondition.await()
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

    fun addMatch(match: ScriptMatch) {
        matches += match
    }

    suspend fun matchWait() {
        if (matches.isEmpty()) {
            throw WslRuntimeException("matchWait called with no matches")
        }

        client.eventFlow.first { event ->
            if (event is ClientTextEvent) {
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
            val roundEnd = client.properties.value["roundtime"]?.toLongOrNull()?.let { it * 1000L } ?: return
            val currentTime = client.time
            if (roundEnd < currentTime) {
                return
            }
            delay(roundEnd - currentTime)
        }
    }

    override fun close() {
        scope.cancel()
    }

    fun addHighlight(
        pattern: String,
        style: StyleDefinition,
        matchPartialWord: Boolean,
        ignoreCase: Boolean,
        isRegex: Boolean,
    ) {
        highlightRegistry.addHighlight(
            client.characterId.value?.lowercase(),
            Highlight(
                pattern = pattern,
                styles = listOf(style),
                matchPartialWord = matchPartialWord,
                ignoreCase = ignoreCase,
                isRegex = isRegex,
            )
        )
    }

    fun deleteHighlight(pattern: String) {
        highlightRegistry.deleteHighlight(client.characterId.value?.lowercase(), pattern)
    }

    fun addListener(name: String, action: (String) -> Unit) {
        listeners[name] = action
    }

    fun removeListener(name: String) {
        listeners.remove(name)
    }
}
