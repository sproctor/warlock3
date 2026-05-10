package warlockfe.warlock3.compose.desktop.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
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
    proxySettings: ConnectionProxySettings,
    updateName: (String) -> Unit,
    updateProxySettings: (ConnectionProxySettings) -> Unit,
    closeDialog: () -> Unit,
) {
    var proxyEnabled by rememberSaveable(proxySettings) { mutableStateOf(proxySettings.enabled) }
    val nameState = rememberTextFieldState(name)
    val proxyCommand = rememberTextFieldState(proxySettings.launchCommand ?: "")
    val proxyHost = rememberTextFieldState(proxySettings.host ?: "")
    val proxyPort = rememberTextFieldState(proxySettings.port ?: "")
    val scope = rememberCoroutineScope()

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

            Text(
                "In the following settings, \"{host}\" and \"{port}\" are replaced by the values for the game server. " +
                    "\"{home}\" is replaced by the user home directory.",
            )

            WarlockCheckboxRow(
                checked = proxyEnabled,
                onCheckedChange = { proxyEnabled = it },
                text = "Enable proxy",
            )

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
