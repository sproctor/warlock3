package warlockfe.warlock3.compose.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import warlockfe.warlock3.compose.MainScreen
import warlockfe.warlock3.compose.desktop.shim.WarlockButton
import warlockfe.warlock3.compose.desktop.ui.dashboard.DesktopDashboardView
import warlockfe.warlock3.compose.desktop.ui.game.DesktopGameView
import warlockfe.warlock3.compose.desktop.ui.sge.DesktopSgeWizard
import warlockfe.warlock3.compose.model.GameState
import warlockfe.warlock3.compose.ui.dashboard.DashboardViewModelFactory
import warlockfe.warlock3.compose.ui.sge.SgeViewModelFactory
import warlockfe.warlock3.core.client.GameCharacter
import warlockfe.warlock3.core.sge.SgeSettings

/**
 * Desktop entry into the shared [MainScreen] navigation host, supplying the Jewel screen UIs as
 * content slots.
 */
@Composable
fun DesktopMainScreen(
    sgeViewModelFactory: SgeViewModelFactory,
    dashboardViewModelFactory: DashboardViewModelFactory,
    gameState: GameState,
    updateCurrentCharacter: (GameCharacter?) -> Unit,
    sgeSettings: SgeSettings,
    sideBarVisible: Boolean,
    modifier: Modifier = Modifier,
) {
    MainScreen(
        sgeViewModelFactory = sgeViewModelFactory,
        dashboardViewModelFactory = dashboardViewModelFactory,
        gameState = gameState,
        updateCurrentCharacter = updateCurrentCharacter,
        sgeSettings = sgeSettings,
        modifier = modifier,
        dashboardContent = { viewModel, connectToSge ->
            DesktopDashboardView(viewModel = viewModel, connectToSGE = connectToSge)
        },
        wizardContent = { viewModel, onCancel ->
            DesktopSgeWizard(viewModel = viewModel, onCancel = onCancel)
        },
        gameContent = { viewModel, navigateToDashboard ->
            DesktopGameView(
                viewModel = viewModel,
                navigateToDashboard = navigateToDashboard,
                sideBarVisible = sideBarVisible,
            )
        },
        errorContent = { message, onDismiss ->
            DesktopErrorScreen(message = message, onDismiss = onDismiss)
        },
    )
}

@Composable
private fun DesktopErrorScreen(
    message: String,
    onDismiss: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(JewelTheme.globalColors.panelBackground),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = message)
        WarlockButton(onClick = onDismiss, text = "OK")
    }
}
