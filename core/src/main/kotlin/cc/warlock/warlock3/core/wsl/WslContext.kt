package cc.warlock.warlock3.core.wsl

import cc.warlock.warlock3.core.ClientNavEvent
import cc.warlock.warlock3.core.ClientPromptEvent
import cc.warlock.warlock3.core.ClientTextEvent
import cc.warlock.warlock3.core.WarlockClient
import kotlinx.coroutines.flow.first

class WslContext(
    val client: WarlockClient,
    val lines: List<WslLine>,
    val scriptInstance: WslScriptInstance,
) {
    private val globalVariables = mutableMapOf<String, WslValue>()
    private val matches = mutableListOf<ScriptMatch>()

    private var currentLine = -1
    val lineNumber: Int
        get() = currentLine + 1
    private var nextLine = 0

    suspend fun executeCommand(commandLine: String) {
        val (commandName, args) = commandLine.splitFirstWord()
        val command = wslCommands[commandName]
            ?: throw WslRuntimeException("Invalid command \"$commandName\" on line $lineNumber")
        command(this, args ?: "")
    }

    fun lookupVariable(name: String): WslValue? {
        return globalVariables[name.lowercase()]
    }

    fun hasVariable(name: String): Boolean {
        return globalVariables.containsKey(name.lowercase())
    }

    fun setVariable(name: String, value: WslValue) {
        globalVariables[name.lowercase()] = value
    }

    fun deleteVariable(name: String) {
        globalVariables.remove(name)
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

    suspend fun waitForText(text: String) {
        client.eventFlow.first {
            it is ClientTextEvent && it.text.contains(text)
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
}
