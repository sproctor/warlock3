package cc.warlock.warlock3.core.wsl

import cc.warlock.warlock3.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class WslContext(
    private val client: WarlockClient,
    val lines: List<WslLine>,
    val scriptInstance: WslScriptInstance,
) : AutoCloseable {
    private val scriptVariables = mutableMapOf<String, WslValue>()

    private val matches = mutableListOf<ScriptMatch>()

    private var currentLine = -1
    val lineNumber: Int
        get() = currentLine + 1
    private var nextLine = 0

    private var loggingLevel = 30

    private val scope = CoroutineScope(Dispatchers.IO)

    var typeAhead = 0

    val mutex = Mutex()

    init {
        client.eventFlow
            .onEach { event ->
                when (event) {
                    is ClientPromptEvent ->
                        mutex.withLock {
                            if (typeAhead > 0)
                                typeAhead--
                        }
                    else -> {}
                }
            }
            .launchIn(scope)
    }

    suspend fun executeCommand(commandLine: String) {
        log(5, "Line $lineNumber: $commandLine")

        val (commandName, args) = commandLine.splitFirstWord()
        val command = wslCommands[commandName.lowercase()]
            ?: throw WslRuntimeException("Invalid command \"$commandName\" on line $lineNumber")
        command(this, args ?: "")
    }

    fun lookupVariable(name: String): WslValue? {
        return scriptVariables[name.lowercase()] ?: client.variables.value[name.lowercase()]?.let { WslString(it) }
    }

    fun hasVariable(name: String): Boolean {
        return scriptVariables.containsKey(name.lowercase()) || client.variables.value.containsKey(name.lowercase())
    }

    fun setStoredVariable(name: String, value: String) {
        log(10, "Setting stored variable \"$name\" to $value")
        client.setVariable(name.lowercase(), value)
    }

    fun deleteStoredVariable(name: String) {
        log(10, "Deleting stored variable: $name")
        client.deleteVariable(name.lowercase())
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
        currentLine = nextLine
        nextLine++
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

    suspend fun sendCommand(command: String) {
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
        nextLine = index
    }

    suspend fun waitForNav() {
        log(5, "waiting for next room")
        client.eventFlow.first { it == ClientNavEvent }
    }

    suspend fun waitForPrompt() {
        log(5, "waiting for next prompt")
        client.eventFlow.first { it == ClientPromptEvent }
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
}
