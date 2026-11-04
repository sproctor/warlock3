package warlockfe.warlock3.compose

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import warlockfe.warlock3.compose.model.GameScreen
import warlockfe.warlock3.compose.model.GameState

/**
 * Retained holder for the app's [GameState]. Because it is a [ViewModel], the navigation state and
 * the live game connection it holds survive Android configuration changes (e.g. rotation) instead
 * of being rebuilt from scratch.
 *
 * [closeScope] outlives this view model; the game is closed on it once this one is cleared.
 */
class AppViewModel(
    private val closeScope: CoroutineScope,
) : ViewModel() {
    val gameState = GameState()

    override fun onCleared() {
        // The activity is finishing for good, not rotating. The game's view model is not in any
        // ViewModelStore, so close it here or its connection stays open until the process dies.
        val screen = gameState.screen as? GameScreen.ConnectedGameState ?: return
        closeScope.launch(Dispatchers.Main) { screen.viewModel.close() }
    }
}
