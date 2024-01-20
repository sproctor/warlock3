package warlockfe.warlock3.compose.ui.game

import androidx.compose.ui.platform.ClipboardManager
import warlockfe.warlock3.compose.components.CompassTheme
import warlockfe.warlock3.core.client.WarlockClient
import warlockfe.warlock3.core.prefs.AliasRepository
import warlockfe.warlock3.core.prefs.CharacterSettingsRepository
import warlockfe.warlock3.core.prefs.HighlightRepository
import warlockfe.warlock3.core.prefs.MacroRepository
import warlockfe.warlock3.core.prefs.PresetRepository
import warlockfe.warlock3.core.prefs.VariableRepository
import warlockfe.warlock3.core.prefs.WindowRepository
import warlockfe.warlock3.core.script.ScriptManager
import warlockfe.warlock3.core.window.StreamRegistry

class GameViewModelFactory(
    private val windowRepository: WindowRepository,
    private val macroRepository: MacroRepository,
    private val variableRepository: VariableRepository,
    private val highlightRepository: HighlightRepository,
    private val presetRepository: PresetRepository,
    private val scriptManager: ScriptManager,
    private val compassTheme: CompassTheme,
    private val characterSettingsRepository: CharacterSettingsRepository,
    private val aliasRepository: AliasRepository,
    private val streamRegistry: StreamRegistry,
) {
    fun create(client: WarlockClient, clipboard: ClipboardManager): GameViewModel =
        GameViewModel(
            client = client,
            windowRepository = windowRepository,
            macroRepository = macroRepository,
            variableRepository = variableRepository,
            scriptManager = scriptManager,
            compassTheme = compassTheme,
            clipboard = clipboard,
            highlightRepository = highlightRepository,
            presetRepository = presetRepository,
            characterSettingsRepository = characterSettingsRepository,
            aliasRepository = aliasRepository,
            streamRegistry = streamRegistry,
        )
}