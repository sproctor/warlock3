package cc.warlock.warlock3.app.views.sge

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import cc.warlock.warlock3.app.viewmodel.SgeViewModel
import cc.warlock.warlock3.app.viewmodel.SgeViewState

@Composable
fun SgeWizard(
    viewModel: SgeViewModel,
) {
    val state = viewModel.state
    when (val currentState = state.value) {
        SgeViewState.SgeAccountSelector -> {
            val account by viewModel.lastAccount.collectAsState(null)
            AccountsView(
                initialUsername = account?.username,
                initialPassword = account?.password,
                onAccountSelect = { newAccount ->
                    println("saving username/password")
                    viewModel.saveAccount(newAccount)
                    viewModel.accountSelected(newAccount)
                }
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

@Composable
fun SgeLoadingView(message: String) {
    Box(Modifier.fillMaxSize()) {
        Text(
            modifier = Modifier.align(Alignment.Center),
            text = message
        )
    }
}