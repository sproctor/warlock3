package cc.warlock.warlock3.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.window.FrameWindowScope
import cc.warlock.warlock3.app.config.ClientSpec
import cc.warlock.warlock3.app.view.GameView
import cc.warlock.warlock3.app.view.SgeWizard
import cc.warlock.warlock3.app.viewmodel.GameViewModel
import cc.warlock.warlock3.app.viewmodel.SgeViewModel
import cc.warlock.warlock3.stormfront.network.StormfrontClient
import com.uchuhimo.konf.Config
import com.uchuhimo.konf.source.hocon.toHocon

@Composable
fun FrameWindowScope.WarlockApp(state: MutableState<GameState>, config: Config) {
    when (val currentState = state.value) {
        GameState.NewGameState -> {
            val viewModel = remember {
                SgeViewModel(
                    config = config,
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
            val viewModel = remember(currentState.key) {
                val client = StormfrontClient(
                    currentState.host,
                    currentState.port,
                    initialVariables = config[ClientSpec.variables],
                    saveVariables = {
                        config[ClientSpec.variables] = it
                        config.toHocon.toFile(preferencesFile)
                    }
                )
                GameViewModel(client).also {
                    client.connect(currentState.key)
                }
            }
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