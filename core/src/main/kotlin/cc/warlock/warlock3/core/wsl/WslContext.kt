package cc.warlock.warlock3.core.wsl

import cc.warlock.warlock3.core.ClientNavEvent
import cc.warlock.warlock3.core.ClientPromptEvent
import cc.warlock.warlock3.core.WarlockClient
import kotlinx.coroutines.flow.first

class WslContext(
    val client: WarlockClient,
    val lines: List<WslLine>,
    val scriptInstance: WslScriptInstance,
) {
    private val globalVariables = mutableMapOf<String, WslValue>()

    private var currentLine = -1
    val lineNumber: Int
        get() = currentLine + 1
    private var nextLine = 0

    fun lookupVariable(name: String): WslValue {
        return globalVariables[name.lowercase()] ?: WslValue.WslString("")
    }

    fun hasVariable(name: String): Boolean {
        return globalVariables.containsKey(name.lowercase())
    }

    fun setVariable(name: String, value: WslValue) {
        globalVariables[name.lowercase()] = value
    }

    fun getNextLine(): WslLine? {
        currentLine = nextLine
        nextLine++
        if (currentLine >= lines.size) {
            return null
        }
        return lines[currentLine]
    }

    fun setNextLine(index: Int) {
        nextLine = index
    }

    fun stop() {
        scriptInstance.stop()
    }

    suspend fun waitForNav() {
        client.eventFlow.first { it == ClientNavEvent }
    }

    suspend fun waitForPrompt() {
        client.eventFlow.first { it == ClientPromptEvent }
    }
}