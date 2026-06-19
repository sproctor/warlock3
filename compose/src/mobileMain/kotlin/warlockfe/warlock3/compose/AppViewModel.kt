package warlockfe.warlock3.compose

import androidx.lifecycle.ViewModel
import warlockfe.warlock3.compose.model.GameState

/**
 * Retained holder for the app's [GameState]. Because it is a [ViewModel], the navigation state and
 * the live game connection it holds survive Android configuration changes (e.g. rotation) instead
 * of being rebuilt from scratch.
 */
class AppViewModel : ViewModel() {
    val gameState = GameState()
}
