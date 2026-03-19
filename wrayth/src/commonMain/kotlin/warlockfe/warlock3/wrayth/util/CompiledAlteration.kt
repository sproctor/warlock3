package warlockfe.warlock3.wrayth.util

import warlockfe.warlock3.core.prefs.models.AlterationEntity

class CompiledAlteration(private val alteration: AlterationEntity) {
    val regex = Regex(
        pattern = alteration.pattern,
        options = setOfNotNull(if (alteration.ignoreCase) RegexOption.IGNORE_CASE else null)
    )

    val replacement = alteration.result

    fun appliesToStream(streamName: String): Boolean {
        return alteration.sourceStream.isNullOrBlank() || streamName.equals(alteration.sourceStream, ignoreCase = true)
    }
}
