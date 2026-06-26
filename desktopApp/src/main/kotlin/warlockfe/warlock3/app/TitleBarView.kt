package warlockfe.warlock3.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
import org.jetbrains.jewel.window.DecoratedWindowScope
import org.jetbrains.jewel.window.TitleBar
import org.jetbrains.jewel.window.utils.DesktopPlatform
import warlockfe.warlock3.compose.generated.resources.Res
import warlockfe.warlock3.compose.generated.resources.space_dashboard
import warlockfe.warlock3.compose.generated.resources.space_dashboard_filled
import warlockfe.warlock3.compose.util.createPlatformDialogSettings
import java.io.File

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun DecoratedWindowScope.TitleBarView(
    title: String,
    sideBarVisible: Boolean,
    showSideBar: (Boolean) -> Unit,
    isConnected: Boolean,
    disconnected: Boolean,
    canReconnect: Boolean,
    reconnect: () -> Unit,
    goToDashboard: () -> Unit,
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
    TitleBar {
        Row(Modifier.align(Alignment.Start).padding(start = 8.dp)) {
            if (isConnected) {
                val windowsShape = RoundedCornerShape(5.dp)
                val contentColor = LocalContentColor.current
                Row(
                    modifier =
                        Modifier
                            .clip(windowsShape)
                            // Outlined "pill" so it reads as a button; fills when the list is open.
                            // Border/background draw within the bounds, so the title bar height is unchanged.
                            .background(
                                if (sideBarVisible) contentColor.copy(alpha = 0.15f) else Color.Transparent,
                            ).border(Dp.Hairline, contentColor.copy(alpha = 0.5f), windowsShape)
                            .clickable { showSideBar(!sideBarVisible) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(
                        modifier = Modifier.size(16.dp),
                        painter =
                            painterResource(
                                if (sideBarVisible) {
                                    Res.drawable.space_dashboard_filled
                                } else {
                                    Res.drawable.space_dashboard
                                },
                            ),
                        colorFilter = ColorFilter.tint(contentColor),
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Windows")
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
                                AppMenuItem("Disconnect", enabled = isConnected && !disconnected, onClick = disconnect),
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
            if (DesktopPlatform.Current == DesktopPlatform.MacOS) {
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
        if (isConnected) {
            Row(
                modifier = Modifier.align(Alignment.End).padding(end = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when {
                    !disconnected -> TitleBarPill(text = "Connected", dotColor = ConnectedColor)

                    // Disconnected: the status indicator becomes a reconnect action when possible.
                    canReconnect -> TitleBarPill(text = "Reconnect", onClick = reconnect)

                    else -> TitleBarPill(text = "Disconnected", dotColor = DisconnectedColor)
                }
                if (disconnected) {
                    TitleBarPill(text = "Dashboard", onClick = goToDashboard)
                }
            }
        }
    }
}

private val ConnectedColor = Color(0xFF86D6A0)
private val DisconnectedColor = Color(0xFFE5484D)

/**
 * A compact title-bar pill matching the "Windows" toggle: a hairline-outlined rounded row that draws
 * within the title-bar bounds (so the bar height is unchanged). Shows an optional status [dotColor]
 * and becomes clickable when [onClick] is supplied.
 */
@Composable
private fun TitleBarPill(
    text: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    dotColor: Color? = null,
) {
    val shape = RoundedCornerShape(5.dp)
    val contentColor = LocalContentColor.current
    Row(
        modifier =
            modifier
                .clip(shape)
                .border(Dp.Hairline, contentColor.copy(alpha = 0.5f), shape)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dotColor != null) {
            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(dotColor))
            Spacer(Modifier.width(6.dp))
        }
        Text(text)
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
