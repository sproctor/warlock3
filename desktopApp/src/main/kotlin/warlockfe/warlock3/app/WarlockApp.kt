package warlockfe.warlock3.app

import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import org.jetbrains.jewel.window.DecoratedWindowScope
import warlockfe.warlock3.compose.AppContainer
import warlockfe.warlock3.compose.MainScreen
import warlockfe.warlock3.compose.model.GameScreen
import warlockfe.warlock3.compose.model.GameState
import warlockfe.warlock3.core.client.GameCharacter
import warlockfe.warlock3.core.sge.SgeSettings

@OptIn(ExperimentalSerializationApi::class)
@Composable
fun DecoratedWindowScope.WarlockApp(
    title: String,
    appContainer: AppContainer,
    gameState: GameState,
    openNewWindow: () -> Unit,
    showUpdateDialog: () -> Unit,
    sgeSettings: SgeSettings,
) {
    var showSettings by remember { mutableStateOf(false) }
    var exportMessage by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    CompositionLocalProvider(
        LocalScrollbarStyle provides LocalScrollbarStyle.current.copy(
            hoverColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            unhoverColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
        )
    ) {
        var showAboutDialog by remember { mutableStateOf(false) }
        var sideBarVisible by remember { mutableStateOf(false) }
        TitleBarView(
            title = title,
            sideBarVisible = sideBarVisible,
            showSideBar = {
                sideBarVisible = it
            },
            isConnected = gameState.screen is GameScreen.ConnectedGameState,
            openNewWindow = openNewWindow,
            showSettingsDialog = {
                showSettings = true
            },
            disconnect = {
                val screen = gameState.screen
                if (screen is GameScreen.ConnectedGameState) {
                    scope.launch {
                        screen.viewModel.close()
                    }
                }
            },
            scriptDirectory = appContainer.scriptDirRepository.getDefaultDir(),
            runScript = {
                val screen = gameState.screen
                if (screen is GameScreen.ConnectedGameState) {
                    screen.viewModel.runScript(it)
                }
            },
            showUpdateDialog = showUpdateDialog,
            showAboutDialog = {
                showAboutDialog = !showAboutDialog
            },
            exportSettings = { file ->
                // TODO: move this out of UI
                scope.launch {
                    try {
                        file.outputStream().use { outputStream ->
                            val exportData = appContainer.exportRepository.getExport()
                            Json.encodeToStream(exportData, outputStream)
                        }
                        exportMessage = "Settings exported successfully"
                    } catch (e: Exception) {
                        exportMessage = "Failed to export settings: ${e.message}"
                    }
                }
            }
        )

        if (exportMessage != null) {
            AlertDialog(
                onDismissRequest = { exportMessage = null },
                text = { Text(exportMessage!!) },
                confirmButton = {
                    TextButton(
                        onClick = { exportMessage = null }
                    ) {
                        Text("OK")
                    }
                }
            )
        }
        if (showAboutDialog) {
            AboutDialog { showAboutDialog = false }
        }

        var currentCharacter: GameCharacter? by remember { mutableStateOf(null) }

        MainScreen(
            sgeViewModelFactory = appContainer.sgeViewModelFactory,
            dashboardViewModelFactory = appContainer.dashboardViewModelFactory,
            gameState = gameState,
            updateCurrentCharacter = { currentCharacter = it },
            sgeSettings = sgeSettings,
            sideBarVisible = sideBarVisible,
        )

        if (showSettings) {
            SettingsDialog(
                currentCharacter = currentCharacter,
                closeDialog = { showSettings = false },
                variableRepository = appContainer.variableRepository,
                macroRepository = appContainer.macroRepository,
                presetRepository = appContainer.presetRepository,
                characterRepository = appContainer.characterRepository,
                highlightRepository = appContainer.highlightRepository,
                nameRepository = appContainer.nameRepository,
                characterSettingsRepository = appContainer.characterSettingsRepository,
                aliasRepository = appContainer.aliasRepository,
                scriptDirRepository = appContainer.scriptDirRepository,
                alterationRepository = appContainer.alterationRepository,
                clientSettingRepository = appContainer.clientSettings,
                wraythImporter = appContainer.wraythImporter,
            )
        }
    }
}
