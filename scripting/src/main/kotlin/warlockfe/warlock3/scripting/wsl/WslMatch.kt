package warlockfe.warlock3.scripting.wsl

sealed class WslMatch(val label: String) {
    abstract fun match(line: String): String?
}

class TextMatch(label: String, val text: String) : WslMatch(label) {
    override fun match(line: String): String? {
        if (line.contains(text, ignoreCase = true)) {
            return text
        }
        return null
    }
}

class RegexMatch(label: String, val regex: Regex) : WslMatch(label) {
    override fun match(line: String): String? {
        return regex.find(line)?.value
    }
}