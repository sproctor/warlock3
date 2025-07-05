package warlockfe.warlock3.compose.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import warlockfe.warlock3.compose.ui.game.GameViewModel
import warlockfe.warlock3.compose.ui.window.StreamRegistryFactory
import warlockfe.warlock3.core.prefs.WindowRepository
import warlockfe.warlock3.core.prefs.WindowRepositoryFactory
import warlockfe.warlock3.core.window.StreamRegistry

class GameState(
    val windowRepository: WindowRepository,
    val streamRegistry: StreamRegistry,
) {
    var screen by mutableStateOf<GameScreen>(GameScreen.Dashboard)
        private set

    suspend fun setScreen(screen: GameScreen) {
        withContext(Dispatchers.Main.immediate) {
            this@GameState.screen = screen
        }
    }
}

class GameStateFactory(
    private val windowRepositoryFactory: WindowRepositoryFactory,
    private val streamRegistryFactory: StreamRegistryFactory,
) {
    fun create(): GameState {
        return GameState(
            windowRepositoryFactory.create(), streamRegistryFactory.create()
        )
    }
}

sealed interface GameScreen {
    data object Dashboard : GameScreen
    data object NewGameState : GameScreen
    data class ConnectedGameState(val viewModel: GameViewModel) : GameScreen

    data class ErrorState(val message: String, val returnTo: GameScreen) : GameScreen
}