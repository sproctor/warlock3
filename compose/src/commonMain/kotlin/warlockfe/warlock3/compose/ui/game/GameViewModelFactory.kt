package warlockfe.warlock3.compose.ui.game

import kotlinx.coroutines.CoroutineDispatcher
import warlockfe.warlock3.compose.components.CompassTheme
import warlockfe.warlock3.core.client.WarlockClient
import warlockfe.warlock3.core.prefs.AliasRepository
import warlockfe.warlock3.core.prefs.AlterationRepository
import warlockfe.warlock3.core.prefs.CharacterSettingsRepository
import warlockfe.warlock3.core.prefs.HighlightRepository
import warlockfe.warlock3.core.prefs.MacroRepository
import warlockfe.warlock3.core.prefs.NameRepository
import warlockfe.warlock3.core.prefs.PresetRepository
import warlockfe.warlock3.core.prefs.VariableRepository
import warlockfe.warlock3.core.prefs.WindowRepository
import warlockfe.warlock3.core.script.ScriptManagerFactory
import warlockfe.warlock3.core.window.StreamRegistry

class GameViewModelFactory(
    private val macroRepository: MacroRepository,
    private val variableRepository: VariableRepository,
    private val highlightRepository: HighlightRepository,
    private val nameRepository: NameRepository,
    private val presetRepository: PresetRepository,
    private val compassTheme: CompassTheme,
    private val characterSettingsRepository: CharacterSettingsRepository,
    private val aliasRepository: AliasRepository,
    private val alterationRepository: AlterationRepository,
    private val scriptManagerFactory: ScriptManagerFactory,
    private val mainDispatcher: CoroutineDispatcher,
) {
    fun create(
        client: WarlockClient,
        windowRepository: WindowRepository,
        streamRegistry: StreamRegistry,
    ): GameViewModel =
        GameViewModel(
            client = client,
            windowRepository = windowRepository,
            macroRepository = macroRepository,
            variableRepository = variableRepository,
            scriptManager = scriptManagerFactory.create(),
            compassTheme = compassTheme,
            highlightRepository = highlightRepository,
            nameRepository = nameRepository,
            presetRepository = presetRepository,
            characterSettingsRepository = characterSettingsRepository,
            aliasRepository = aliasRepository,
            alterationRepository = alterationRepository,
            streamRegistry = streamRegistry,
            mainDispatcher = mainDispatcher,
        )
}
