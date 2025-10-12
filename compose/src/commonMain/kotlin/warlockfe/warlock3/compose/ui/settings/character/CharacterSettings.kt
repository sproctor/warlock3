package warlockfe.warlock3.compose.ui.settings.character

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import warlockfe.warlock3.core.sge.ConnectionProxySettings

@Composable
fun CharacterSettingsDialog(
    proxySettings: ConnectionProxySettings,
    updateProxySettings: (ConnectionProxySettings) -> Unit,
    closeDialog: () -> Unit,
) {
    var proxyEnabled by rememberSaveable(proxySettings) { mutableStateOf(proxySettings.enabled) }
    val proxyCommand = rememberTextFieldState(proxySettings.launchCommand ?: "")
    val proxyHost = rememberTextFieldState(proxySettings.host ?: "")
    var proxyPort = rememberTextFieldState(proxySettings.port ?: "")
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = closeDialog,
        confirmButton = {
            TextButton(
                onClick = {
                    scope.launch {
                        updateProxySettings(
                            ConnectionProxySettings(
                                enabled = proxyEnabled,
                                launchCommand = proxyCommand.text.toString().ifBlank { null },
                                host = proxyHost.text.toString().ifBlank { null },
                                port = proxyPort.text.toString().ifBlank { null },
                            )
                        )
                        closeDialog()
                    }
                }
            ) {
                Text("OK")
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("In the following settings, \"{host}\" and \"{port}\" are replaced by the values for the game server. \"{home}\" is replaced by the user home directory.")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = proxyEnabled,
                        onCheckedChange = {
                            proxyEnabled = it
                        }
                    )
                    Spacer(Modifier.width(16.dp))
                    Text("Enable proxy")
                }
                TextField(
                    state = proxyCommand,
                    label = {
                        Text("Lich/proxy launch command")
                    },
                    placeholder = {
                        Text("ruby lich.rbw -g {host}:{port}")
                    },
                    lineLimits = TextFieldLineLimits.SingleLine,
                )
                TextField(
                    state = proxyHost,
                    label = {
                        Text("Proxy host")
                    },
                    placeholder = {
                        Text("localhost")
                    },
                    lineLimits = TextFieldLineLimits.SingleLine,
                )
                TextField(
                    state = proxyPort,
                    label = {
                        Text("Proxy port")
                    },
                    placeholder = {
                        Text("{port}")
                    },
                    lineLimits = TextFieldLineLimits.SingleLine,
                )
            }
        },
    )
}
