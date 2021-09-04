package cc.warlock.warlock3.core.wsl

import cc.warlock.warlock3.core.WarlockClient

class WslContext(
    val client: WarlockClient,
    val lines: List<WslLine>,
    val scriptInstance: WslScriptInstance,
) {
    private val globalVariables = HashMap<String, WslValue>()

    private var currentLine = 0
    val lineNumber: Int
        get() = currentLine + 1
    private var nextLine = 1

    fun lookupVariable(name: String): WslValue {
        return WslValue.WslString("")
    }

    fun hasVariable(name: String): Boolean {
        return false
    }

    fun setVariable(name: String, value: WslValue) {
        globalVariables[name] = value
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
}