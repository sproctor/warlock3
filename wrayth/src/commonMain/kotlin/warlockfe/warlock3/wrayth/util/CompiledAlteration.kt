package warlockfe.warlock3.wrayth.util

import warlockfe.warlock3.core.prefs.models.AlterationEntity

class CompiledAlteration(private val alteration: AlterationEntity) {
    private val regex =
        Regex(alteration.pattern, setOfNotNull(if (alteration.ignoreCase) RegexOption.IGNORE_CASE else null))

    fun appliesToStream(streamName: String): Boolean {
        return alteration.sourceStream.isNullOrBlank() || streamName.equals(alteration.sourceStream, ignoreCase = true)
    }

    fun match(line: String): AlterationResult? {
        return try {
            regex.find(line)?.let {
                AlterationResult(
                    text = alteration.result,
                    matchResult = it,
                )
            }
        } catch (_: Exception) {
            // TODO: notify user
            null
        }
    }
}

data class AlterationResult(
    val text: String?,
    val matchResult: MatchResult,
)