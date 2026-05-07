package warlockfe.warlock3.compose.desktop.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import warlockfe.warlock3.compose.desktop.shim.WarlockButton
import warlockfe.warlock3.compose.desktop.shim.WarlockListItem
import warlockfe.warlock3.compose.desktop.shim.WarlockOutlinedButton
import warlockfe.warlock3.compose.desktop.shim.WarlockScrollableColumn
import warlockfe.warlock3.compose.desktop.ui.settings.DesktopConfirmationDialog
import warlockfe.warlock3.compose.ui.dashboard.DashboardViewModel
import warlockfe.warlock3.core.sge.StoredConnection

@Suppress("ktlint:compose:vm-forwarding-check")
@Composable
fun DesktopDashboardView(
    viewModel: DashboardViewModel,
    connectToSGE: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(JewelTheme.globalColors.panelBackground)
            .padding(16.dp),
    ) {
        WarlockButton(
            onClick = connectToSGE,
            text = "Create a new connection",
            enabled = !viewModel.busy,
        )
        Spacer(Modifier.height(16.dp))
        viewModel.message?.let { message ->
            Text(message)
            Spacer(Modifier.height(16.dp))
        }
        if (!viewModel.busy) {
            DesktopConnectionList(
                modifier = Modifier.weight(1f),
                viewModel = viewModel,
            )
        } else {
            WarlockButton(onClick = { viewModel.cancelConnect() }, text = "Cancel")
        }
    }
}

@Suppress("ktlint:compose:vm-forwarding-check")
@Composable
private fun DesktopConnectionList(
    viewModel: DashboardViewModel,
    modifier: Modifier = Modifier,
) {
    var showConnectionSettings: StoredConnection? by remember { mutableStateOf(null) }
    var showConnectionDelete: StoredConnection? by remember { mutableStateOf(null) }
    val connections by viewModel.connections.collectAsState(emptyList())

    WarlockScrollableColumn(
        modifier =
            modifier.semantics {
                this.contentDescription = "List of stored connections"
            },
    ) {
        Text("Saved connections")
        Spacer(Modifier.height(8.dp))
        if (connections.isEmpty()) {
            Text("There are currently no stored connections")
        }
        connections.forEach { connection ->
            WarlockListItem(
                modifier =
                    Modifier.semantics {
                        contentDescription = "Saved connection"
                    },
                leading = {
                    WarlockButton(
                        onClick = { viewModel.connect(connection) },
                        text = "Login",
                    )
                },
                headline = { Text(connection.name) },
                trailing = {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        WarlockOutlinedButton(
                            onClick = { showConnectionSettings = connection },
                            text = "Edit",
                        )
                        WarlockOutlinedButton(
                            onClick = { showConnectionDelete = connection },
                            text = "Delete",
                        )
                    }
                },
            )
        }
    }
    val editingConnection = showConnectionSettings
    if (editingConnection != null) {
        DesktopConnectionSettingsDialog(
            name = editingConnection.name,
            proxySettings = editingConnection.proxySettings,
            updateName = { viewModel.renameConnection(editingConnection.id, it) },
            updateProxySettings = { viewModel.updateProxySettings(editingConnection.id, it) },
            closeDialog = { showConnectionSettings = null },
        )
    }
    if (showConnectionDelete != null) {
        DesktopConfirmationDialog(
            title = "Delete connection",
            text = "Are you sure that you want to delete: ${showConnectionDelete!!.name}",
            onDismiss = { showConnectionDelete = null },
            onConfirm = {
                viewModel.deleteConnection(showConnectionDelete!!.id)
                showConnectionDelete = null
            },
        )
    }
}
