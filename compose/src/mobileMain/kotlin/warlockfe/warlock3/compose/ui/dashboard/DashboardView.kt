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
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecureTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import org.jetbrains.compose.resources.painterResource
import warlockfe.warlock3.compose.components.ConfirmationDialog
import warlockfe.warlock3.compose.components.ScrollableLazyColumn
import warlockfe.warlock3.compose.generated.resources.Res
import warlockfe.warlock3.compose.generated.resources.add
import warlockfe.warlock3.compose.generated.resources.delete
import warlockfe.warlock3.compose.generated.resources.edit
import warlockfe.warlock3.compose.generated.resources.login
import warlockfe.warlock3.core.sge.StoredConnection

@Suppress("ktlint:compose:vm-forwarding-check")
@Composable
fun DashboardView(
    viewModel: DashboardViewModel,
    connectToSGE: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(16.dp),
        ) {
            Button(
                onClick = connectToSGE,
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                enabled = !viewModel.busy,
            ) {
                Icon(painter = painterResource(Res.drawable.add), contentDescription = null)
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
                    },
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
fun ConnectionList(
    viewModel: DashboardViewModel,
    modifier: Modifier = Modifier,
) {
    var showConnectionSettings: StoredConnection? by remember { mutableStateOf(null) }
    var showConnectionDelete: StoredConnection? by remember { mutableStateOf(null) }
    var passwordPrompt: StoredConnection? by remember { mutableStateOf(null) }
    val connections by viewModel.connections.collectAsState(emptyList())
    ScrollableLazyColumn(
        modifier.semantics {
            this.contentDescription = "List of stored connections"
        },
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
                modifier =
                    Modifier.semantics {
                        contentDescription = "Saved connection"
                    },
                headlineContent = { Text(connection.name) },
                // supportingContent = { Text(character.gameCode) },
                leadingContent = {
                    IconButton(
                        onClick = {
                            // Prompt to set the account password when none is saved, rather than
                            // attempting a doomed empty-password login.
                            if (connection.password.isNullOrBlank()) {
                                passwordPrompt = connection
                            } else {
                                viewModel.connect(connection)
                            }
                        },
                    ) {
                        Icon(painter = painterResource(Res.drawable.login), contentDescription = "Login")
                    }
                },
                trailingContent = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(
                            onClick = {
                                showConnectionSettings = connection
                            },
                        ) {
                            Icon(painter = painterResource(Res.drawable.edit), contentDescription = "Edit connection")
                        }
                        IconButton(
                            onClick = {
                                showConnectionDelete = connection
                            },
                        ) {
                            Icon(painter = painterResource(Res.drawable.delete), contentDescription = "Delete connection")
                        }
                    }
                },
            )
        }
    }
    val editingConnection = showConnectionSettings
    if (editingConnection != null) {
        ConnectionSettingsDialog(
            name = editingConnection.name,
            windowTitle = editingConnection.windowTitle,
            proxySettings = editingConnection.proxySettings,
            updateName = {
                viewModel.renameConnection(editingConnection.id, it)
            },
            updateWindowTitle = {
                viewModel.updateWindowTitle(editingConnection.id, it)
            },
            updateProxySettings = {
                viewModel.updateProxySettings(editingConnection.id, it)
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
            },
        )
    }
    passwordPrompt?.let { connection ->
        PasswordPromptDialog(
            connection = connection,
            onConnect = { password ->
                viewModel.updatePasswordAndConnect(connection, password)
                passwordPrompt = null
            },
            onDismiss = { passwordPrompt = null },
        )
    }
}

@Composable
private fun PasswordPromptDialog(
    connection: StoredConnection,
    onConnect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val passwordState = rememberTextFieldState(connection.password ?: "")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Password required") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("No password is saved for account \"${connection.username}\". Enter it to log in.")
                SecureTextField(
                    state = passwordState,
                    label = { Text("Password") },
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = passwordState.text.isNotBlank(),
                onClick = { onConnect(passwordState.text.toString()) },
            ) {
                Text("Login")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
