package warlockfe.warlock3.stormfront.util

import warlockfe.warlock3.core.prefs.models.AlterationEntity

class CompiledAlteration(private val alteration: AlterationEntity) {
    private val regex =
        Regex(alteration.pattern, setOfNotNull(if (alteration.ignoreCase) RegexOption.IGNORE_CASE else null))

    fun match(line: String, streamName: String): AlterationResult? {
        if (alteration.sourceStream != null && streamName != alteration.sourceStream)
            return null
        return if (regex.containsMatchIn(line)) {
            AlterationResult(
                text = alteration.result?.let { regex.replace(line, it) },
                alteration = alteration
            )
        } else {
            null
        }
    }
}

data class AlterationResult(
    val text: String?, // null means keep original text
    val alteration: AlterationEntity
)