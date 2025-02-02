package warlockfe.warlock3.compose.ui.settings.character

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
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
    var proxyEnabled by remember(proxySettings) { mutableStateOf(proxySettings.enabled) }
    var proxyCommand by remember(proxySettings) { mutableStateOf(proxySettings.launchCommand ?: "") }
    var proxyHost by remember(proxySettings) { mutableStateOf(proxySettings.host ?: "") }
    var proxyPort by remember(proxySettings) { mutableStateOf(proxySettings.port ?: "") }
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
                                launchCommand = proxyCommand.ifBlank { null },
                                host = proxyHost.ifBlank { null },
                                port = proxyPort.ifBlank { null },
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
                Text("In the following settings, \"{host}\" and \"{port}\" are replaced by the values for the game server.")
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
                    value = proxyCommand,
                    onValueChange = {
                        proxyCommand = it
                    },
                    label = {
                        Text("Lich/proxy launch command")
                    },
                    placeholder = {
                        Text("ruby lich.rbw -g {host}:{port}")
                    }
                )
                TextField(
                    value = proxyHost,
                    onValueChange = {
                        proxyHost = it
                    },
                    label = {
                        Text("Proxy host")
                    },
                    placeholder = {
                        Text("localhost")
                    },
                )
                TextField(
                    value = proxyPort,
                    onValueChange = {
                        proxyPort = it
                    },
                    label = {
                        Text("Proxy port")
                    },
                    placeholder = {
                        Text("{port}")
                    }
                )
            }
        },
    )
}
