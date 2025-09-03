package warlockfe.warlock3.scripting.wsl

import warlockfe.warlock3.core.prefs.HighlightRepository
import warlockfe.warlock3.core.prefs.NameRepository
import warlockfe.warlock3.core.prefs.VariableRepository
import warlockfe.warlock3.core.util.SoundPlayer

class WslEngineFactory(
    private val highlightRepository: HighlightRepository,
    private val nameRepository: NameRepository,
    private val variableRepository: VariableRepository,
    private val soundPlayer: SoundPlayer,
) {
    fun create(): WslEngine {
        return WslEngine(
            highlightRepository = highlightRepository,
            nameRepository = nameRepository,
            variableRepository = variableRepository,
            soundPlayer = soundPlayer,
        )
    }
}