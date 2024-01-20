package warlockfe.warlock3.compose.model

import warlockfe.warlock3.compose.ui.game.GameViewModel

sealed class GameState {
    object Dashboard : GameState()
    object NewGameState : GameState()
    data class ConnectedGameState(val viewModel: GameViewModel) : GameState()

    data class ErrorState(val message: String) : GameState()
}
