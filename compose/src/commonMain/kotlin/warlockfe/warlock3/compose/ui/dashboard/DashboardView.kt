package warlockfe.warlock3.compose.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import warlockfe.warlock3.compose.components.ConfirmationDialog
import warlockfe.warlock3.compose.components.ScrollableColumn
import warlockfe.warlock3.compose.icons.Login
import warlockfe.warlock3.compose.ui.settings.character.CharacterSettingsDialog
import warlockfe.warlock3.core.sge.StoredConnection

@Composable
fun DashboardView(
    viewModel: DashboardViewModel,
    connectToSGE: () -> Unit,
) {
    Surface {
        Column(
            Modifier.fillMaxSize()
                .padding(16.dp)
        ) {
            Button(
                onClick = connectToSGE,
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                enabled = !viewModel.busy,
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(text = "Connect a new character")
            }
            Spacer(Modifier.height(16.dp))
            if (viewModel.message != null) {
                Text(text = viewModel.message!!)
                Spacer(Modifier.height(16.dp))
            }
            if (!viewModel.busy) {
                ConnectionList(
                    modifier = Modifier.weight(1f),
                    viewModel = viewModel,
                )
            } else {
                Button(
                    onClick = {
                        viewModel.cancelConnect()
                    }
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
fun ConnectionList(
    modifier: Modifier,
    viewModel: DashboardViewModel,
) {
    var showConnectionSettings: StoredConnection? by remember { mutableStateOf(null) }
    var showConnectionDelete: StoredConnection? by remember { mutableStateOf(null) }
    val connections by viewModel.connections.collectAsState(emptyList())
    ScrollableColumn(modifier) {
        Text(text = "Connections", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        connections.forEach { connection ->
            ListItem(
                headlineContent = { Text(connection.name) },
                // supportingContent = { Text(character.gameCode) },
                leadingContent = {
                    IconButton(
                        onClick = {
                            viewModel.connect(connection)
                        },
                    ) {
                        Icon(imageVector = Login, contentDescription = null)
                    }
                },
                trailingContent = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(
                            onClick = {
                                showConnectionSettings = connection
                            },
                        ) {
                            Icon(imageVector = Icons.Default.Edit, contentDescription = null)
                        }
                        IconButton(
                            onClick = {
                                showConnectionDelete = connection
                            }
                        ) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = null)
                        }
                    }
                }
            )
        }
    }
    val proxySettings = showConnectionSettings?.proxySettings
    if (proxySettings != null) {
            CharacterSettingsDialog(
                proxySettings = proxySettings,
                updateProxySettings = {
                    viewModel.updateProxySettings(showConnectionSettings!!.id, it)
                },
                closeDialog = { showConnectionSettings = null },
            )
    }
    if (showConnectionDelete != null) {
        ConfirmationDialog(
            title = "Delete character",
            text = "Are you sure that you want to delete: ${showConnectionDelete!!.name}",
            onDismiss = { showConnectionDelete = null },
            onConfirm = {
                viewModel.deleteConnection(showConnectionDelete!!.id)
                showConnectionDelete = null
            }
        )
    }
}
