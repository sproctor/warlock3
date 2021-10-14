package cc.warlock.warlock3.app.view

import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import cc.warlock.warlock3.app.config.SgeSpec
import cc.warlock.warlock3.app.preferencesFile
import cc.warlock.warlock3.app.viewmodel.SgeViewModel
import cc.warlock.warlock3.app.viewmodel.SgeViewState
import com.uchuhimo.konf.source.hocon.toHocon

@Composable
fun SgeWizard(
    viewModel: SgeViewModel,
) {
    val state = viewModel.state
    when (val currentState = state.value) {
        SgeViewState.SgeAccountSelector -> AccountsView(
            initialUsername = viewModel.config[SgeSpec.username],
            initialPassword = viewModel.config[SgeSpec.password],
            onAccountSelect = {
                println("saving username/password")
                viewModel.config[SgeSpec.username] = it.name
                viewModel.config[SgeSpec.password] = it.password
                viewModel.config.toHocon.toFile(preferencesFile)
                viewModel.accountSelected(it)
            }
        )
        is SgeViewState.SgeGameSelector -> SgeGameView(
            games = currentState.games,
            onBackPressed = { viewModel.goBack() },
            onGameSelected = { viewModel.gameSelected(it) },
        )
        is SgeViewState.SgeCharacterSelector -> SgeCharacterView(
            characters = currentState.characters,
            onBackPressed = { viewModel.goBack() },
            onCharacterSelected = { viewModel.characterSelected(it) }
        )
        is SgeViewState.SgeLoading -> Text("Loading: ${currentState.message}")
        is SgeViewState.SgeError -> SgeErrorView(currentState.error, backPressed = { viewModel.goBack() })
        else -> Text("Unimplemented")
    }
}