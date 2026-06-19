package warlockfe.warlock3.compose.ui.game

import kotlinx.coroutines.CoroutineDispatcher
import warlockfe.warlock3.core.client.WarlockClient
import warlockfe.warlock3.core.prefs.repositories.ActionRepository
import warlockfe.warlock3.core.prefs.repositories.AliasRepository
import warlockfe.warlock3.core.prefs.repositories.CharacterSettingsRepository
import warlockfe.warlock3.core.prefs.repositories.ClientSettingRepository
import warlockfe.warlock3.core.prefs.repositories.CommandHistoryRepository
import warlockfe.warlock3.core.prefs.repositories.ConnectionRepository
import warlockfe.warlock3.core.prefs.repositories.MacroRepository
import warlockfe.warlock3.core.prefs.repositories.PresetRepository
import warlockfe.warlock3.core.prefs.repositories.ProgressBarSettingRepository
import warlockfe.warlock3.core.prefs.repositories.VariableRepository
import warlockfe.warlock3.core.prefs.repositories.WindowSettingsRepository
import warlockfe.warlock3.core.script.ScriptManagerFactory
import warlockfe.warlock3.core.window.WindowRegistry

class GameViewModelFactory(
    private val macroRepository: MacroRepository,
    private val variableRepository: VariableRepository,
    private val presetRepository: PresetRepository,
    private val characterSettingsRepository: CharacterSettingsRepository,
    private val aliasRepository: AliasRepository,
    private val actionRepository: ActionRepository,
    private val scriptManagerFactory: ScriptManagerFactory,
    private val windowSettingsRepository: WindowSettingsRepository,
    private val progressBarSettingRepository: ProgressBarSettingRepository,
    private val clientSettingRepository: ClientSettingRepository,
    private val commandHistoryRepository: CommandHistoryRepository,
    private val connectionRepository: ConnectionRepository,
    private val ioDispatcher: CoroutineDispatcher,
) {
    fun create(
        client: WarlockClient,
        windowRegistry: WindowRegistry,
        reconnect: (suspend () -> Unit)? = null,
    ): GameViewModel =
        GameViewModel(
            client = client,
            windowSettingsRepository = windowSettingsRepository,
            macroRepository = macroRepository,
            variableRepository = variableRepository,
            scriptManager = scriptManagerFactory.create(),
            presetRepository = presetRepository,
            characterSettingsRepository = characterSettingsRepository,
            aliasRepository = aliasRepository,
            actionRepository = actionRepository,
            windowRegistry = windowRegistry,
            progressBarSettingRepository = progressBarSettingRepository,
            clientSettingRepository = clientSettingRepository,
            commandHistoryRepository = commandHistoryRepository,
            connectionRepository = connectionRepository,
            ioDispatcher = ioDispatcher,
            reconnectAction = reconnect,
        )
}
