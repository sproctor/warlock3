package warlockfe.warlock3.compose.ui.game

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import warlockfe.warlock3.compose.AppContainer
import warlockfe.warlock3.compose.GameConnectResult
import warlockfe.warlock3.compose.GameConnection
import warlockfe.warlock3.compose.GameStarter
import warlockfe.warlock3.compose.model.GameScreen
import warlockfe.warlock3.compose.model.GameState
import warlockfe.warlock3.core.client.WarlockClient
import warlockfe.warlock3.core.window.WindowRegistry

/**
 * Builds a game's view model from [container]'s services and puts the game on a window's screen.
 */
class GameViewModelFactory(
    private val container: AppContainer,
) {
    /**
     * A [GameStarter] that puts its games on [gameState]'s screen, through [start]. A reconnect
     * that fails shows an error screen that goes back to [returnTo].
     */
    fun starter(
        gameState: GameState,
        returnTo: GameScreen = GameScreen.Dashboard,
    ): GameStarter = GameStarter { connection -> start(connection, gameState, returnTo) }

    /**
     * Builds the game screen for [connection]'s client, starts the client reading with its key, and
     * shows the game on [gameState]. The order is the point: the client's eventFlow has no replay,
     * and the server sends the first windows and the compass the moment it is connected, so the
     * view model has to be listening before the read loop starts or those events go nowhere. Every
     * connection path comes through here for that.
     *
     * The connection is ours from the moment this is called. If the client fails to start, or the
     * caller is cancelled before the game is on screen, its client and window registry are closed
     * here along with the view model, which nothing else would ever close: it is in no
     * ViewModelStore, and until it is on screen no one has it.
     *
     * A reconnect that fails puts its reason on [gameState]'s screen, which goes back to [returnTo].
     */
    suspend fun start(
        connection: GameConnection,
        gameState: GameState,
        returnTo: GameScreen = GameScreen.Dashboard,
    ) {
        val reconnect: (suspend () -> Unit)? =
            connection.reconnect?.let { redial ->
                {
                    val result = redial()
                    if (result is GameConnectResult.Failure) gameState.showError(result.message, returnTo)
                }
            }
        val viewModel =
            create(connection.client, connection.windowRegistry, reconnect) { stoppedScripts ->
                // By now the reconnect has put its game on screen, unless it failed to an error.
                (gameState.screen as? GameScreen.ConnectedGameState)
                    ?.viewModel
                    ?.reportScriptsStoppedByReconnect(stoppedScripts)
            }
        var shown = false
        try {
            connection.client.connect(connection.key)
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
        with(container) {
            GameViewModel(
                client = client,
                windowSettingsRepository = windowSettingRepository,
                macroRepository = macroRepository,
                variableRepository = variableRepository,
                scriptManager = scriptManagerFactory.create(),
                presetRepository = presetRepository,
                characterSettingsRepository = characterSettingsRepository,
                aliasRepository = aliasRepository,
                actionRepository = actionRepository,
                windowRegistry = windowRegistry,
                progressBarSettingRepository = progressBarSettingRepository,
                clientSettingRepository = clientSettings,
                commandHistoryRepository = commandHistoryRepository,
                connectionRepository = connectionRepository,
                mudScriptStore = mudScriptStore,
                ioDispatcher = ioDispatcher,
                reconnectAction = reconnect,
                onReconnectStoppedScripts = onReconnectStoppedScripts,
            )
        }
}
