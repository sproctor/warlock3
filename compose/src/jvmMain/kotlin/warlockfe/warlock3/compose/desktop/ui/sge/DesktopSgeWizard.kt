package warlockfe.warlock3.compose.desktop.ui.sge

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import warlockfe.warlock3.compose.ui.sge.SgeViewModel
import warlockfe.warlock3.compose.ui.sge.SgeViewState

@Suppress("ktlint:compose:vm-forwarding-check")
@Composable
fun DesktopSgeWizard(
    viewModel: SgeViewModel,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(JewelTheme.globalColors.panelBackground),
    ) {
        when (val currentState = viewModel.state.value) {
            SgeViewState.SgeAccountSelector -> {
                val account by viewModel.lastAccount.collectAsState(null)
                DesktopAccountsView(
                    initialUsername = account?.username,
                    initialPassword = account?.password,
                    onAccountSelect = { newAccount ->
                        viewModel.saveAccount(newAccount)
                        viewModel.accountSelected(newAccount)
                    },
                    onCancel = onCancel,
                )
            }
            SgeViewState.SgeLoadingGameList -> DesktopSgeLoadingView("Loading game list")
            is SgeViewState.SgeGameSelector ->
                DesktopSgeGameView(
                    games = currentState.games,
                    onBackPress = { viewModel.goBack() },
                    onGameSelect = { viewModel.gameSelected(it) },
                )
            is SgeViewState.SgeLoadingCharacterList ->
                DesktopSgeLoadingView("Loading character list for game: ${currentState.game.code}")
            is SgeViewState.SgeCharacterSelector ->
                DesktopSgeCharacterView(
                    characters = currentState.characters,
                    onBackPress = { viewModel.goBack() },
                    onCharacterSelect = { character ->
                        viewModel.characterSelected(currentState.game, character)
                    },
                )
            is SgeViewState.SgeConnecting -> DesktopSgeLoadingView("Connecting to SGE server")
            is SgeViewState.SgeError ->
                DesktopSgeErrorView(currentState.error, backPressed = { viewModel.goBack() })
            is SgeViewState.SgeConnectingToGame -> Unit
        }
    }
}

@Composable
fun DesktopSgeLoadingView(
    message: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        Text(modifier = Modifier.align(Alignment.Center), text = message)
    }
}
