package cc.warlock.warlock3.core.script.wsl

sealed class ScriptMatch(val label: String) {
    abstract fun match(line: String): String?
}

class TextMatch(label: String, val text: String) : ScriptMatch(label) {
    override fun match(line: String): String? {
        if (line.contains(text, ignoreCase = true)) {
            return text
        }
        return null
    }
}

class RegexMatch(label: String, val regex: Regex) : ScriptMatch(label) {
    override fun match(line: String): String? {
        return regex.find(line)?.value
    }
}