package warlockfe.warlock3.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.nucleus.core.runtime.Platform
import io.github.kdroidfilter.nucleus.window.DecoratedWindowScope
import io.github.kdroidfilter.nucleus.window.jewel.JewelTitleBar
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.dialogs.compose.rememberFileSaverLauncher
import io.github.vinceglb.filekit.utils.toKotlinxIoPath
import kotlinx.io.files.Path
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.jewel.foundation.modifier.onHover
import org.jetbrains.jewel.foundation.theme.LocalContentColor
import org.jetbrains.jewel.ui.component.PopupMenu
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.separator
import warlockfe.warlock3.compose.desktop.shim.WarlockIconButton
import warlockfe.warlock3.compose.generated.resources.Res
import warlockfe.warlock3.compose.generated.resources.space_dashboard_filled
import warlockfe.warlock3.compose.generated.resources.space_dashboard_outlined
import warlockfe.warlock3.compose.util.createPlatformDialogSettings
import java.io.File

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun DecoratedWindowScope.TitleBarView(
    title: String,
    sideBarVisible: Boolean,
    showSideBar: (Boolean) -> Unit,
    isConnected: Boolean,
    openNewWindow: () -> Unit,
    showSettingsDialog: () -> Unit,
    disconnect: () -> Unit,
    scriptDirectory: String?,
    runScript: (Path) -> Unit,
    showUpdateDialog: () -> Unit,
    showAboutDialog: () -> Unit,
    exportSettings: (File) -> Unit,
    exportCharacterSettings: (File) -> Unit,
    importSettings: (File) -> Unit,
    importWraythSettings: () -> Unit,
    currentCharacterName: String?,
) {
    val scriptFilePickerLauncher =
        rememberFilePickerLauncher(
            dialogSettings = FileKitDialogSettings.createPlatformDialogSettings("Run script"),
            directory = scriptDirectory?.let { PlatformFile(it) },
        ) { file ->
            if (file != null) {
                runScript(file.file.toKotlinxIoPath())
            }
        }
    val exportFileSaveLauncher =
        rememberFileSaverLauncher { file ->
            if (file != null) {
                exportSettings(file.file)
            }
        }
    val exportCharacterFileSaveLauncher =
        rememberFileSaverLauncher { file ->
            if (file != null) {
                exportCharacterSettings(file.file)
            }
        }
    val importFilePickerLauncher =
        rememberFilePickerLauncher(
            dialogSettings = FileKitDialogSettings.createPlatformDialogSettings("Import settings"),
        ) { file ->
            if (file != null) {
                importSettings(file.file)
            }
        }
    JewelTitleBar {
        Row(Modifier.align(Alignment.Start)) {
            if (isConnected) {
                WarlockIconButton(onClick = { showSideBar(!sideBarVisible) }) {
                    Image(
                        painter =
                            painterResource(
                                if (sideBarVisible) {
                                    Res.drawable.space_dashboard_filled
                                } else {
                                    Res.drawable.space_dashboard_outlined
                                },
                            ),
                        colorFilter = ColorFilter.tint(LocalContentColor.current),
                        contentDescription = null,
                    )
                }
            }
            val menus =
                listOf(
                    AppMenu(
                        title = "File",
                        items =
                            listOf(
                                AppMenuItem("New window", onClick = openNewWindow),
                                AppMenuItem("Run script...", enabled = isConnected, onClick = { scriptFilePickerLauncher.launch() }),
                                null,
                                AppMenuItem(
                                    label = "Export all settings...",
                                    onClick = {
                                        exportFileSaveLauncher.launch(suggestedName = "settings", defaultExtension = "json")
                                    },
                                ),
                                AppMenuItem(
                                    label = "Export current character...",
                                    enabled = currentCharacterName != null,
                                    onClick = {
                                        exportCharacterFileSaveLauncher.launch(
                                            suggestedName = currentCharacterName ?: "character",
                                            defaultExtension = "json",
                                        )
                                    },
                                ),
                                AppMenuItem("Import settings...", onClick = { importFilePickerLauncher.launch() }),
                                AppMenuItem("Import wrayth settings...", onClick = importWraythSettings),
                                AppMenuItem("Settings...", onClick = showSettingsDialog),
                                null,
                                AppMenuItem("Disconnect", enabled = isConnected, onClick = disconnect),
                            ),
                    ),
                    AppMenu(
                        title = "Help",
                        items =
                            listOf(
                                AppMenuItem("Updates", onClick = showUpdateDialog),
                                AppMenuItem("About", onClick = showAboutDialog),
                            ),
                    ),
                )
            if (Platform.Current == Platform.MacOS) {
                AppMenuBar(menus)
            } else {
                // The native MenuBar isn't shown off macOS, so render the same menus as popups.
                var openMenu by remember { mutableStateOf<String?>(null) }
                menus.forEach { menu ->
                    Box {
                        MenuTriggerButton(
                            text = menu.title,
                            // Hover switches menus only while one is already open (matching a menu bar).
                            onHover = { if (openMenu != null) openMenu = menu.title },
                            onClick = { openMenu = if (openMenu == menu.title) null else menu.title },
                        )
                        if (openMenu == menu.title) {
                            PopupMenu(
                                onDismissRequest = {
                                    openMenu = null
                                    true
                                },
                                horizontalAlignment = Alignment.Start,
                            ) {
                                menu.items.forEach { item ->
                                    if (item == null) {
                                        separator()
                                    } else {
                                        selectableItem(
                                            selected = false,
                                            enabled = item.enabled,
                                            onClick = item.onClick,
                                        ) {
                                            Text(item.label)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Text(
            modifier = Modifier.align(Alignment.CenterHorizontally),
            text = title,
        )
    }
}

@Composable
private fun MenuTriggerButton(
    text: String,
    onHover: () -> Unit,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .onHover { hovered -> if (hovered) onHover() }
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(text)
    }
}
