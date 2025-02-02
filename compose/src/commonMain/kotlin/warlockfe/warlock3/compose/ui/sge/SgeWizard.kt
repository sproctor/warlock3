package warlockfe.warlock3.compose.ui.sge

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun SgeWizard(
    viewModel: SgeViewModel,
    onCancel: () -> Unit,
) {
    Surface(Modifier.fillMaxSize()) {
        val state = viewModel.state
        when (val currentState = state.value) {
            SgeViewState.SgeAccountSelector -> {
                val account by viewModel.lastAccount.collectAsState(null)
                AccountsView(
                    initialUsername = account?.username,
                    initialPassword = account?.password,
                    onAccountSelect = { newAccount ->
                        viewModel.saveAccount(newAccount)
                        viewModel.accountSelected(newAccount)
                    },
                    onCancel = onCancel
                )
            }

            SgeViewState.SgeLoadingGameList -> SgeLoadingView("Loading game list")
            is SgeViewState.SgeGameSelector -> SgeGameView(
                games = currentState.games,
                onBackPressed = { viewModel.goBack() },
                onGameSelected = { viewModel.gameSelected(it) },
            )

            is SgeViewState.SgeLoadingCharacterList -> SgeLoadingView("Loading character list for game: ${currentState.game.code}")
            is SgeViewState.SgeCharacterSelector -> SgeCharacterView(
                characters = currentState.characters,
                onBackPressed = { viewModel.goBack() },
                onCharacterSelected = { character ->
                    viewModel.characterSelected(currentState.game, character)
                }
            )

            is SgeViewState.SgeConnecting -> SgeLoadingView("Connecting to SGE server")
            is SgeViewState.SgeError -> SgeErrorView(currentState.error, backPressed = { viewModel.goBack() })
            is SgeViewState.SgeConnectingToGame -> Unit // TODO: implement?
        }
    }
}

@Composable
fun SgeLoadingView(message: String) {
    Box(Modifier.fillMaxSize()) {
        Text(
            modifier = Modifier.align(Alignment.Center),
            text = message
        )
    }
}