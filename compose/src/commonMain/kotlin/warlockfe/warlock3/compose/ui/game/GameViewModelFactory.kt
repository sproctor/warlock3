package warlockfe.warlock3.compose.ui.game

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import warlockfe.warlock3.compose.model.GameScreen
import warlockfe.warlock3.compose.model.GameState
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
import warlockfe.warlock3.core.script.MudScriptStore
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
    private val mudScriptStore: MudScriptStore,
    private val ioDispatcher: CoroutineDispatcher,
) {
    /**
     * Builds the game screen for [client], starts the client reading with [key], and shows the game
     * on [gameState]. The order is the point: the client's eventFlow has no replay, and the server
     * sends the first windows and the compass the moment it is connected, so the view model has to
     * be listening before the read loop starts or those events go nowhere. Every connection path
     * comes through here for that.
     *
     * [client] and [windowRegistry] are ours from the moment this is called. If the client fails to
     * start, or the caller is cancelled before the game is on screen, they are closed here along
     * with the view model, which nothing else would ever close: it is in no ViewModelStore, and
     * until it is on screen no one has it.
     */
    suspend fun connect(
        client: WarlockClient,
        key: String,
        windowRegistry: WindowRegistry,
        gameState: GameState,
        reconnect: (suspend () -> Unit)? = null,
    ) {
        val viewModel =
            create(client, windowRegistry, reconnect) { stoppedScripts ->
                // By now the reconnect has put its game on screen, unless it failed to an error.
                (gameState.screen as? GameScreen.ConnectedGameState)
                    ?.viewModel
                    ?.reportScriptsStoppedByReconnect(stoppedScripts)
            }
        var shown = false
        try {
            client.connect(key)
            // Once the client is reading, the game goes on screen even if we are cancelled now:
            // showing it clears the dashboard's view model, which is what cancels most callers.
            withContext(NonCancellable) {
                gameState.setScreen(GameScreen.ConnectedGameState(viewModel))
                shown = true
            }
        } finally {
            if (!shown) viewModel.discard()
        }
    }

    private fun create(
        client: WarlockClient,
        windowRegistry: WindowRegistry,
        reconnect: (suspend () -> Unit)?,
        onReconnectStoppedScripts: suspend (names: List<String>) -> Unit,
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
            mudScriptStore = mudScriptStore,
            ioDispatcher = ioDispatcher,
            reconnectAction = reconnect,
            onReconnectStoppedScripts = onReconnectStoppedScripts,
        )
}
