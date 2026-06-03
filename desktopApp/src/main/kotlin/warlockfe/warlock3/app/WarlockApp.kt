package warlockfe.warlock3.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import io.github.kdroidfilter.nucleus.window.DecoratedWindowScope
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.utils.toKotlinxIoPath
import kotlinx.coroutines.launch
import warlockfe.warlock3.compose.AppContainer
import warlockfe.warlock3.compose.desktop.shim.WarlockAlertDialog
import warlockfe.warlock3.compose.desktop.shim.WarlockButton
import warlockfe.warlock3.compose.desktop.ui.DesktopMainScreen
import warlockfe.warlock3.compose.model.GameScreen
import warlockfe.warlock3.compose.model.GameState
import warlockfe.warlock3.compose.util.createPlatformDialogSettings
import warlockfe.warlock3.core.client.GameCharacter
import warlockfe.warlock3.core.prefs.export.WarlockExportFile
import warlockfe.warlock3.core.sge.SgeSettings

@Composable
fun DecoratedWindowScope.WarlockApp(
    title: String,
    warlockVersion: String,
    appContainer: AppContainer,
    gameState: GameState,
    openNewWindow: () -> Unit,
    showUpdateDialog: () -> Unit,
    sgeSettings: SgeSettings,
) {
    var showSettings by remember { mutableStateOf(false) }
    var transferMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var wraythImportMessages by remember { mutableStateOf<List<String>?>(null) }
    var showWraythCharacterSelect by remember { mutableStateOf(false) }
    var wraythTarget by remember { mutableStateOf("global") }
    var pendingImport by remember { mutableStateOf<WarlockExportFile?>(null) }
    var currentCharacter: GameCharacter? by remember { mutableStateOf(null) }
    val characters by appContainer.characterRepository.observeAllCharacters().collectAsState(emptyList())
    val transfer = appContainer.settingsTransferUseCase
    val scope = rememberCoroutineScope()
    var showAboutDialog by remember { mutableStateOf(false) }
    var sideBarVisible by remember { mutableStateOf(false) }
    val wraythFileLauncher =
        rememberFilePickerLauncher(
            dialogSettings = FileKitDialogSettings.createPlatformDialogSettings("Choose Wrayth settings file to import"),
        ) { file ->
            if (file != null) {
                scope.launch {
                    wraythImportMessages =
                        appContainer.wraythImporter.importFile(wraythTarget, file.file.toKotlinxIoPath())
                }
            }
        }
    TitleBarView(
        title = title,
        sideBarVisible = sideBarVisible,
        showSideBar = { sideBarVisible = it },
        isConnected = gameState.screen is GameScreen.ConnectedGameState,
        openNewWindow = openNewWindow,
        showSettingsDialog = { showSettings = true },
        disconnect = {
            val screen = gameState.screen
            if (screen is GameScreen.ConnectedGameState) {
                scope.launch { screen.viewModel.close() }
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
        showAboutDialog = { showAboutDialog = !showAboutDialog },
        exportSettings = { file ->
            scope.launch {
                transferMessage =
                    try {
                        file.writeText(transfer.exportAll())
                        "Settings exported successfully"
                    } catch (e: Exception) {
                        "Failed to export settings: ${e.message}"
                    }
            }
        },
        exportCharacterSettings = { file ->
            scope.launch {
                val character = currentCharacter
                transferMessage =
                    if (character == null) {
                        "No character selected to export"
                    } else {
                        try {
                            file.writeText(transfer.exportCharacter(character.id))
                            "Character exported successfully"
                        } catch (e: Exception) {
                            "Failed to export character: ${e.message}"
                        }
                    }
            }
        },
        importSettings = { file ->
            scope.launch {
                try {
                    pendingImport = transfer.parse(file.readText())
                } catch (e: Exception) {
                    transferMessage = "Failed to read import file: ${e.message}"
                }
            }
        },
        importWraythSettings = { showWraythCharacterSelect = true },
        currentCharacterName = currentCharacter?.name,
    )

    if (showWraythCharacterSelect) {
        SelectCharacterDialog(
            title = "Import Wrayth settings into",
            confirmText = "Choose file...",
            characters = characters,
            onCancel = { showWraythCharacterSelect = false },
            onSelect = { character ->
                showWraythCharacterSelect = false
                wraythTarget = character?.id ?: "global"
                wraythFileLauncher.launch()
            },
        )
    }

    wraythImportMessages?.let { messages ->
        WraythImportResultDialog(messages = messages, onClose = { wraythImportMessages = null })
    }

    when (val import = pendingImport) {
        is WarlockExportFile.SingleCharacter ->
            ImportCharacterDialog(
                character = import.character,
                characters = characters,
                onCancel = { pendingImport = null },
                onImport = { targetCharacterId, mode ->
                    pendingImport = null
                    scope.launch {
                        transferMessage =
                            try {
                                transfer.importCharacter(import.character, targetCharacterId, mode)
                                "Character imported successfully"
                            } catch (e: Exception) {
                                "Failed to import character: ${e.message}"
                            }
                    }
                },
            )

        is WarlockExportFile.Full ->
            ImportFullDialog(
                export = import.export,
                existingCharacterIds = characters.map { it.id }.toSet(),
                onCancel = { pendingImport = null },
                onImport = { resolutions ->
                    pendingImport = null
                    scope.launch {
                        transferMessage =
                            try {
                                transfer.importFull(import.export, resolutions)
                                "Settings imported successfully"
                            } catch (e: Exception) {
                                "Failed to import settings: ${e.message}"
                            }
                    }
                },
            )

        null -> {}
    }

    if (transferMessage != null) {
        WarlockAlertDialog(
            title = "Settings",
            text = transferMessage!!,
            onDismissRequest = { transferMessage = null },
            confirmButton = {
                WarlockButton(onClick = { transferMessage = null }, text = "OK")
            },
        )
    }
    if (showAboutDialog) {
        AboutDialog(warlockVersion) { showAboutDialog = false }
    }

    DesktopMainScreen(
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
        )
    }
}
