package warlockfe.warlock3.scripting.wsl

import warlockfe.warlock3.core.prefs.repositories.HighlightRepositoryImpl
import warlockfe.warlock3.core.prefs.repositories.NameRepositoryImpl
import warlockfe.warlock3.core.prefs.repositories.VariableRepository
import warlockfe.warlock3.core.util.SoundPlayer

class WslEngineFactory(
    private val highlightRepository: HighlightRepositoryImpl,
    private val nameRepository: NameRepositoryImpl,
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