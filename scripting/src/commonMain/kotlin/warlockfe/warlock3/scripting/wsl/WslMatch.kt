package warlockfe.warlock3.scripting.wsl

sealed class WslMatch(val label: String) {
    abstract fun match(line: String, frame: WslFrame): String?
}

class TextMatch(label: String, val text: String) : WslMatch(label) {
    override fun match(line: String, frame: WslFrame): String? {
        if (line.contains(text, ignoreCase = true)) {
            return text
        }
        return null
    }
}

class RegexMatch(label: String, val regex: Regex) : WslMatch(label) {
    override fun match(line: String, frame: WslFrame): String? {
        return regex.find(line)?.also { matchResult ->
            val matchMap = matchResult.groups.mapIndexed { index, group ->
                index.toString() to (group?.value?.let { WslString(it) } ?: WslNull)
            }.toMap()
            frame.setVariable("match", WslMap(matchMap))
        }
            ?.value
    }
}