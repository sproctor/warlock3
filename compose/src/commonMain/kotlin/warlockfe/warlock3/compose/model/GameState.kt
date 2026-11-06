package warlockfe.warlock3.compose.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import warlockfe.warlock3.compose.GameConnectResult
import warlockfe.warlock3.compose.ui.game.GameViewModel
import warlockfe.warlock3.core.client.WarlockSocket
import warlockfe.warlock3.core.sge.SimuGameCredentials
import warlockfe.warlock3.core.sge.StoredConnection

/**
 * One window's game: what it shows, and what it connects to when its dashboard first appears.
 *
 * @param startupConnection the connection to open on first showing the dashboard; null for a
 *   window that should just show the dashboard (a manually opened additional window).
 */
class GameState(
    startupConnection: StartupConnection? = StartupConnection.LastConnection,
) {
    var screen by mutableStateOf<GameScreen>(GameScreen.Dashboard)
        private set

    private var pendingStartupConnection: StartupConnection? = startupConnection

    /**
     * The connection to open on startup, handed out once: returning to the dashboard after a
     * disconnect must not connect again.
     */
    fun takeStartupConnection(): StartupConnection? = pendingStartupConnection.also { pendingStartupConnection = null }

    suspend fun setScreen(screen: GameScreen) {
        withContext(Dispatchers.Main.immediate) {
            this@GameState.screen = screen
        }
    }

    /** Puts [result] on the error screen, which goes back to [returnTo], if it is a failure. */
    suspend fun showFailure(
        result: GameConnectResult,
        returnTo: GameScreen,
    ) {
        if (result is GameConnectResult.Failure) setScreen(GameScreen.ErrorState(result.message, returnTo))
    }
}

sealed interface GameScreen {
    data object Dashboard : GameScreen

    data object NewGameState : GameScreen

    data class ConnectedGameState(
        val viewModel: GameViewModel,
    ) : GameScreen

    data class ErrorState(
        val message: String,
        val returnTo: GameScreen,
    ) : GameScreen
}

/** What a window connects to when its dashboard first appears. */
sealed interface StartupConnection {
    /** The last launched connection, if the user turned auto-connect on. */
    data object LastConnection : StartupConnection

    /** A saved connection, named on the command line. */
    data class Stored(
        val connection: StoredConnection,
    ) : StartupConnection

    /** A game server and key given on the command line, skipping the login server. */
    data class Credentials(
        val credentials: SimuGameCredentials,
    ) : StartupConnection

    /** A game read from a stream (a log file being replayed, or stdin) instead of a server. */
    class Stream(
        val open: () -> WarlockSocket,
    ) : StartupConnection
}
