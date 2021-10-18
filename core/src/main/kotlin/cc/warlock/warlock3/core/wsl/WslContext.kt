package cc.warlock.warlock3.core.wsl

import cc.warlock.warlock3.core.ClientNavEvent
import cc.warlock.warlock3.core.ClientPromptEvent
import cc.warlock.warlock3.core.ClientTextEvent
import cc.warlock.warlock3.core.WarlockClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

class WslContext(
    val client: WarlockClient,
    val lines: List<WslLine>,
    val scriptInstance: WslScriptInstance,
) {
    private val scriptVariables = mutableMapOf<String, WslValue>()

    private val matches = mutableListOf<ScriptMatch>()

    private var currentLine = -1
    val lineNumber: Int
        get() = currentLine + 1
    private var nextLine = 0

    suspend fun executeCommand(commandLine: String) {
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
        client.setVariable(name.lowercase(), value)
    }

    fun deleteStoredVariable(name: String) {
        client.deleteVariable(name.lowercase())
    }

    fun setScriptVariable(name: String, value: WslValue) {
        scriptVariables += name to value
    }

    fun deleteScriptVariable(name: String) {
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

    fun goto(label: String) {
        var index = lines.indexOfFirst { line ->
            line.labels.any { it.equals(other = label, ignoreCase = true) }
        }
        if (index == -1) {
            index = lines.indexOfFirst { line ->
                line.labels.any { it.equals(other = "labelError", ignoreCase = true) }
            }
        }
        if (index == -1) {
            throw WslRuntimeException("Could not find label \"$label\".")
        }
        nextLine = index
    }

    suspend fun waitForNav() {
        client.eventFlow.first { it == ClientNavEvent }
    }

    suspend fun waitForPrompt() {
        client.eventFlow.first { it == ClientPromptEvent }
    }

    suspend fun waitForText(text: String, ignoreCase: Boolean) {
        client.eventFlow.first {
            it is ClientTextEvent && it.text.contains(other = text, ignoreCase = ignoreCase)
        }
    }

    suspend fun waitForRegex(regex: Regex) {
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
                matches.forEach { match ->
                    match.match(event.text)?.let { text ->
                        println("got match: $text")
                        goto(match.label)
                        return@first true
                    }
                }
            }
            false
        }
    }

    suspend fun waitForRoundTime() {
        while(true) {
            val roundEnd = client.properties.value["roundtime"]?.toLongOrNull()?.let { it * 1000L } ?: return
            val currentTime = client.time
            if (roundEnd < currentTime) {
                return
            }
            delay(roundEnd - currentTime)
        }
    }
}
