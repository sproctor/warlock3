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
import warlockfe.warlock3.core.prefs.models.AccountEntity

@Composable
fun SgeWizard(
    viewModel: SgeViewModel,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier.fillMaxSize()) {
        val state = viewModel.state
        when (val currentState = state.value) {
            SgeViewState.SgeAccountSelector -> {
                val accounts: List<AccountEntity>? by viewModel.accounts.collectAsState(initial = null)
                accounts?.let { loaded ->
                    SgeAccountSelectorView(
                        accounts = loaded,
                        onAccountSelect = { account ->
                            viewModel.accountSelected(account)
                        },
                        onSaveAccount = { account ->
                            viewModel.saveAccount(account)
                            viewModel.accountSelected(account)
                        },
                        onCancel = onCancel,
                    )
                } ?: SgeLoadingView("Loading accounts")
            }

            SgeViewState.SgeLoadingGameList -> {
                SgeLoadingView("Loading game list")
            }

            is SgeViewState.SgeGameSelector -> {
                SgeGameView(
                    games = currentState.games,
                    onBackPress = { viewModel.goBack() },
                    onGameSelect = { viewModel.gameSelected(it) },
                )
            }

            is SgeViewState.SgeLoadingCharacterList -> {
                SgeLoadingView("Loading character list for game: ${currentState.game.code}")
            }

            is SgeViewState.SgeCharacterSelector -> {
                SgeCharacterView(
                    characters = currentState.characters,
                    onBackPress = { viewModel.goBack() },
                    onCharacterSelect = { character ->
                        viewModel.characterSelected(currentState.game, character)
                    },
                )
            }

            is SgeViewState.SgeConnecting -> {
                SgeLoadingView("Connecting to SGE server")
            }

            is SgeViewState.SgeError -> {
                SgeErrorView(currentState.error, backPressed = { viewModel.goBack() })
            }

            is SgeViewState.SgeConnectingToGame -> {
                Unit
            } // TODO: implement?
        }
    }
}

@Composable
fun SgeLoadingView(
    message: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        Text(
            modifier = Modifier.align(Alignment.Center),
            text = message,
        )
    }
}
