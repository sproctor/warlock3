package cc.warlock.warlock3.app.view

import androidx.compose.material.Text
import androidx.compose.runtime.*
import cc.warlock.warlock3.app.viewmodel.SgeViewModel
import cc.warlock.warlock3.app.viewmodel.SgeViewState
import org.apache.commons.configuration2.Configuration
import org.apache.commons.configuration2.builder.fluent.Configurations
import org.apache.commons.configuration2.ex.ConfigurationException

@Composable
fun SgeWizard(
    viewModel: SgeViewModel,
) {
    val state = viewModel.state.collectAsState()
    when (val currentState = state.value) {
        SgeViewState.SgeAccountSelector -> AccountsView(
            initialUsername = viewModel.config?.getString("sge.username"),
            initialPassword = viewModel.config?.getString("sge.password"),
            onAccountSelect = {
                println("saving username/password")
                viewModel.config?.setProperty("sge.username", it.name)
                viewModel.config?.setProperty("sge.password", it.password)
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