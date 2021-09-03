package cc.warlock.warlock3.core.wsl

import cc.warlock.warlock3.core.WarlockClient

class WslContext(
    val client: WarlockClient,
) {
    private val globalVariables = HashMap<String, WslValue>()

    private var currentLine = 0

    fun lookupVariable(name: String): WslValue {
        return WslValue.WslString("")
    }

    fun hasVariable(name: String): Boolean {
        return false
    }

    fun setVariable(name: String, value: WslValue) {
        globalVariables[name] = value
    }

    fun getCurrentLine(): Int {
        return currentLine
    }

    fun incrementLine() {
        currentLine += 1
    }
}