package warlockfe.warlock3.compose.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import warlockfe.warlock3.compose.ui.game.GameViewModel

class GameState {
    var screen by mutableStateOf<GameScreen>(GameScreen.Dashboard)
        private set

    // Guards the once-per-window "auto-connect last connection on startup" attempt. Set true once the
    // attempt has been consumed (or pre-set true for windows that should never auto-connect, e.g. a
    // window already connected via the command line, or a manually opened additional window).
    var autoConnectAttempted: Boolean = false

    suspend fun setScreen(screen: GameScreen) {
        withContext(Dispatchers.Main.immediate) {
            this@GameState.screen = screen
        }
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
