package cc.warlock.warlock3.app

import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import cc.warlock.warlock3.app.view.GameView
import cc.warlock.warlock3.app.view.SgeWizard
import cc.warlock.warlock3.app.viewmodel.GameViewModel
import cc.warlock.warlock3.app.viewmodel.SgeViewModel

@Composable
fun WarlockApp(state: MutableState<GameState>) {
    when (val currentState = state.value) {
        GameState.NewGameState -> {
            val viewModel = remember {
                SgeViewModel(
                    readyToPlay = { properties ->
                        val key = properties["KEY"]
                        val host = properties["GAMEHOST"]
                        val port = properties["GAMEPORT"]?.toInt()
                        state.value = GameState.ConnectedGameState(host = host!!, port = port!!, key = key!!)
                    }
                )
            }
            SgeWizard(viewModel = viewModel)
        }
        is GameState.ConnectedGameState -> {
            val viewModel = remember(currentState.key) { GameViewModel() }
            viewModel.connect(currentState.host, currentState.port, currentState.key)
            GameView(viewModel)
        }
    }
}

@Composable
fun rememberGameState(): MutableState<GameState> {
    return remember { mutableStateOf(GameState.NewGameState) }
}

sealed class GameState {
    object NewGameState : GameState()
    data class ConnectedGameState(val host: String, val port: Int, val key: String) : GameState()
}