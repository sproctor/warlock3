package warlockfe.warlock3.compose.desktop.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import warlockfe.warlock3.compose.desktop.shim.WarlockButton
import warlockfe.warlock3.compose.desktop.shim.WarlockCheckboxRow
import warlockfe.warlock3.compose.desktop.shim.WarlockDialog
import warlockfe.warlock3.compose.desktop.shim.WarlockOutlinedButton
import warlockfe.warlock3.compose.desktop.shim.WarlockTextField
import warlockfe.warlock3.core.sge.ConnectionProxySettings

@Suppress("ktlint:compose:modifier-missing-check")
@Composable
fun DesktopConnectionSettingsDialog(
    name: String,
    windowTitle: String?,
    proxySettings: ConnectionProxySettings,
    updateName: (String) -> Unit,
    updateWindowTitle: (String?) -> Unit,
    updateProxySettings: (ConnectionProxySettings) -> Unit,
    closeDialog: () -> Unit,
) {
    var proxyEnabled by rememberSaveable(proxySettings) { mutableStateOf(proxySettings.enabled) }
    var proxyEdited by rememberSaveable(proxySettings) { mutableStateOf(false) }
    val nameState = rememberTextFieldState(name)
    val windowTitleState = rememberTextFieldState(windowTitle ?: "")
    val proxyCommand = rememberTextFieldState(proxySettings.launchCommand ?: "")
    val proxyHost = rememberTextFieldState(proxySettings.host ?: "")
    val proxyPort = rememberTextFieldState(proxySettings.port ?: "")
    val scope = rememberCoroutineScope()

    // Adding proxy details to an empty proxy enables it by default. Editing the details while the
    // proxy is disabled instead flags them as edited so we can warn that they will not take effect.
    LaunchedEffect(proxySettings) {
        var hadDetails = proxySettings.hasDetails
        var first = true
        snapshotFlow {
            Triple(proxyCommand.text.toString(), proxyHost.text.toString(), proxyPort.text.toString())
        }.collect { (command, host, port) ->
            val hasDetails = command.isNotBlank() || host.isNotBlank() || port.isNotBlank()
            when {
                first -> {
                    first = false
                }

                proxyEnabled -> {
                    proxyEdited = false
                }

                !hadDetails && hasDetails -> {
                    proxyEnabled = true
                    proxyEdited = false
                }

                else -> {
                    proxyEdited = hasDetails
                }
            }
            hadDetails = hasDetails
        }
    }

    WarlockDialog(
        title = "Connection settings",
        onCloseRequest = closeDialog,
        width = 560.dp,
        height = 520.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Name")
            WarlockTextField(state = nameState, modifier = Modifier.fillMaxWidth())

            Text("Window title")
            WarlockTextField(
                state = windowTitleState,
                modifier = Modifier.fillMaxWidth(),
                placeholder = "Leave blank to use the character name",
            )

            Text(
                "In the following settings, \"{host}\" and \"{port}\" are replaced by the values for the game server. " +
                    "\"{home}\" is replaced by the user home directory.",
            )

            WarlockCheckboxRow(
                checked = proxyEnabled,
                onCheckedChange = { proxyEnabled = it },
                text = "Enable proxy",
            )

            if (proxyEdited && !proxyEnabled) {
                Text(
                    "Proxy settings edited, but the proxy is not enabled and will not be used.",
                    color = JewelTheme.globalColors.text.warning,
                )
            }

            Text("Lich/proxy launch command")
            WarlockTextField(
                state = proxyCommand,
                modifier = Modifier.fillMaxWidth(),
                placeholder = "ruby lich.rbw -g {host}:{port}",
            )

            Text("Proxy host")
            WarlockTextField(
                state = proxyHost,
                modifier = Modifier.fillMaxWidth(),
                placeholder = "localhost",
            )

            Text("Proxy port")
            WarlockTextField(
                state = proxyPort,
                modifier = Modifier.fillMaxWidth(),
                placeholder = "{port}",
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                WarlockOutlinedButton(onClick = closeDialog, text = "Cancel")
                WarlockButton(
                    onClick = {
                        scope.launch {
                            val newName = nameState.text.toString().trim()
                            if (newName.isNotEmpty() && newName != name) {
                                updateName(newName)
                            }
                            val trimmedTitle = windowTitleState.text.toString().trim()
                            val newWindowTitle = trimmedTitle.ifBlank { null }
                            if (newWindowTitle != windowTitle) {
                                updateWindowTitle(newWindowTitle)
                            }
                            updateProxySettings(
                                ConnectionProxySettings(
                                    enabled = proxyEnabled,
                                    launchCommand = proxyCommand.text.toString().ifBlank { null },
                                    host = proxyHost.text.toString().ifBlank { null },
                                    port = proxyPort.text.toString().ifBlank { null },
                                ),
                            )
                            closeDialog()
                        }
                    },
                    text = "OK",
                )
            }
        }
    }
}
