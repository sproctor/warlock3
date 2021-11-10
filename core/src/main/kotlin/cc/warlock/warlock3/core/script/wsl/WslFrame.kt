package cc.warlock.warlock3.core.script.wsl

class WslFrame(startLine: Int) {
    private var currentLine = -1
    val lineNumber: Int
        get() = currentLine + 1
    private var nextLine = startLine
    private val localVariables = mutableMapOf<String, WslValue>()

    fun nextLine(): Int {
        currentLine = nextLine
        nextLine++
        return currentLine
    }

    fun goto(index: Int) {
        nextLine = index
    }

    fun setVariable(name: String, value: WslValue) {
        localVariables[name] = value
    }

    fun lookupVariable(name: String): WslValue? {
        return localVariables[name]
    }
}