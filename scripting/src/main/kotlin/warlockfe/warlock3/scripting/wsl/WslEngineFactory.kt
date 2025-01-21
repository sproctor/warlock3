package warlockfe.warlock3.scripting.wsl

import warlockfe.warlock3.core.prefs.HighlightRepository
import warlockfe.warlock3.core.prefs.VariableRepository

class WslEngineFactory(
    private val highlightRepository: HighlightRepository,
    private val variableRepository: VariableRepository,
) {
    fun create(): WslEngine {
        return WslEngine(
            highlightRepository = highlightRepository,
            variableRepository = variableRepository,
        )
    }
}