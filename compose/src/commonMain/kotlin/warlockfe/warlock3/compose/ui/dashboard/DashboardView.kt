package warlockfe.warlock3.compose.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import warlockfe.warlock3.compose.components.ConfirmationDialog
import warlockfe.warlock3.compose.components.ScrollableLazyColumn
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
                Text(text = "Create a new connection")
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
    ScrollableLazyColumn(
        modifier.semantics {
            this.contentDescription = "List of stored connections"
        }
    ) {
        item {
            Text(
                modifier = Modifier.semantics { heading() },
                text = "Saved connections",
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(Modifier.height(8.dp))
            if (connections.isEmpty()) {
                Text("There are currently no stored connections")
            }
        }
        items(connections) { connection ->
            ListItem(
                modifier = Modifier.semantics {
                    contentDescription = "Saved connection"
                },
                headlineContent = { Text(connection.name) },
                // supportingContent = { Text(character.gameCode) },
                leadingContent = {
                    IconButton(
                        onClick = {
                            viewModel.connect(connection)
                        },
                    ) {
                        Icon(imageVector = Login, contentDescription = "Login")
                    }
                },
                trailingContent = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(
                            onClick = {
                                showConnectionSettings = connection
                            },
                        ) {
                            Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit connection")
                        }
                        IconButton(
                            onClick = {
                                showConnectionDelete = connection
                            }
                        ) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete connection")
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
            title = "Delete connection",
            text = "Are you sure that you want to delete: ${showConnectionDelete!!.name}",
            onDismiss = { showConnectionDelete = null },
            onConfirm = {
                viewModel.deleteConnection(showConnectionDelete!!.id)
                showConnectionDelete = null
            }
        )
    }
}
