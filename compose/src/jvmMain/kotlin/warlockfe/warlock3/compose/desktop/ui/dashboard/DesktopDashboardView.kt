package warlockfe.warlock3.compose.desktop.ui.dashboard

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import sh.calvin.reorderable.ReorderableColumn
import warlockfe.warlock3.compose.desktop.shim.WarlockButton
import warlockfe.warlock3.compose.desktop.shim.WarlockDialog
import warlockfe.warlock3.compose.desktop.shim.WarlockDropdownSelect
import warlockfe.warlock3.compose.desktop.shim.WarlockEditableTextDropdown
import warlockfe.warlock3.compose.desktop.shim.WarlockMenuButton
import warlockfe.warlock3.compose.desktop.shim.WarlockOutlinedButton
import warlockfe.warlock3.compose.desktop.shim.WarlockPasswordField
import warlockfe.warlock3.compose.desktop.shim.WarlockScrollableColumn
import warlockfe.warlock3.compose.desktop.shim.WarlockTextField
import warlockfe.warlock3.compose.desktop.ui.settings.DesktopConfirmationDialog
import warlockfe.warlock3.compose.ui.dashboard.DashboardViewModel
import warlockfe.warlock3.compose.ui.dashboard.MUD_MOBILE_GAME_CODES
import warlockfe.warlock3.compose.ui.dashboard.MudMobileAccent
import warlockfe.warlock3.compose.ui.dashboard.connectionSubline
import warlockfe.warlock3.core.mudmobile.SyncStatus
import warlockfe.warlock3.core.prefs.models.AccountEntity
import warlockfe.warlock3.core.sge.StoredConnection

// Below this window width the action rail collapses into a toolbar + MUD Mobile menu (design D).
private val RAIL_BREAKPOINT = 720.dp

/**
 * Holds the transient dialog state for the dashboard so it can be shared between the rail/toolbar
 * (which open the MUD Mobile dialogs) and the connection list (which opens password/edit/delete).
 */
private class DashboardUiState {
    var showTokenDialog by mutableStateOf(false)
    var showAddCharacterDialog by mutableStateOf(false)
    var passwordPrompt: StoredConnection? by mutableStateOf(null)
    var editConnection: StoredConnection? by mutableStateOf(null)
    var deleteConnection: StoredConnection? by mutableStateOf(null)
}

@Suppress("ktlint:compose:vm-forwarding-check")
@Composable
fun DesktopDashboardView(
    viewModel: DashboardViewModel,
    connectToSGE: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ui = remember { DashboardUiState() }
    val connections by viewModel.connections.collectAsState(emptyList())
    val token by viewModel.mudMobileToken.collectAsState(null)
    val mudMobileConnected = token != null

    Box(
        modifier
            .fillMaxSize()
            .background(JewelTheme.globalColors.panelBackground)
            .padding(16.dp),
    ) {
        when {
            connections.isEmpty() && !mudMobileConnected -> {
                FirstRunPanel(
                    onCreate = connectToSGE,
                    onConnectMudMobile = { ui.showTokenDialog = true },
                )
            }

            else -> {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val wide = maxWidth >= RAIL_BREAKPOINT
                    if (wide) {
                        WideLayout(viewModel, connections, mudMobileConnected, connectToSGE, ui)
                    } else {
                        NarrowLayout(viewModel, connections, mudMobileConnected, connectToSGE, ui)
                    }
                }
            }
        }

        DashboardDialogs(viewModel, ui)
    }
}

// ---------------------------------------------------------------------------------------------
// Layouts
// ---------------------------------------------------------------------------------------------

@Suppress("ktlint:compose:vm-forwarding-check")
@Composable
private fun WideLayout(
    viewModel: DashboardViewModel,
    connections: List<StoredConnection>,
    mudMobileConnected: Boolean,
    connectToSGE: () -> Unit,
    ui: DashboardUiState,
) {
    Row(modifier = Modifier.fillMaxSize()) {
        // Action rail: primary action + MUD Mobile controls stay pinned while the list scrolls.
        Column(
            modifier = Modifier.width(220.dp).fillMaxHeight().padding(end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            WarlockButton(
                onClick = connectToSGE,
                text = "Create a new connection",
                enabled = !viewModel.busy,
                modifier = Modifier.fillMaxWidth(),
            )
            viewModel.message?.takeIf { !viewModel.busy }?.let { Text(it) }
            MudMobileRailBlock(viewModel, mudMobileConnected, ui)
        }
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            ConnectionListPane(viewModel, connections, mudMobileConnected, ui)
        }
    }
}

@Suppress("ktlint:compose:vm-forwarding-check")
@Composable
private fun NarrowLayout(
    viewModel: DashboardViewModel,
    connections: List<StoredConnection>,
    mudMobileConnected: Boolean,
    connectToSGE: () -> Unit,
    ui: DashboardUiState,
) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WarlockButton(
                onClick = connectToSGE,
                text = "Create a new connection",
                enabled = !viewModel.busy,
            )
            Spacer(Modifier.weight(1f))
            MudMobileMenu(viewModel, mudMobileConnected, ui)
        }
        viewModel.message?.takeIf { !viewModel.busy }?.let { Text(it) }
        viewModel.mudMobileMessage?.let { Text(it) }
        ConnectionListPane(viewModel, connections, mudMobileConnected, ui)
    }
}

// ---------------------------------------------------------------------------------------------
// MUD Mobile controls
// ---------------------------------------------------------------------------------------------

@Suppress("ktlint:compose:vm-forwarding-check")
@Composable
private fun MudMobileRailBlock(
    viewModel: DashboardViewModel,
    connected: Boolean,
    ui: DashboardUiState,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("MUD Mobile", fontWeight = FontWeight.SemiBold)
        viewModel.mudMobileMessage?.let { Text(it) }
        if (!connected) {
            Text("Not connected. Play through MUD Mobile's hosted Lich.")
            WarlockOutlinedButton(
                onClick = { ui.showTokenDialog = true },
                text = "Connect to MUD Mobile",
                enabled = !viewModel.mudMobileBusy,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            WarlockOutlinedButton(
                onClick = { ui.showAddCharacterDialog = true },
                text = "Add a character",
                enabled = !viewModel.mudMobileBusy,
                modifier = Modifier.fillMaxWidth(),
            )
            WarlockOutlinedButton(
                onClick = { viewModel.refreshMudMobileCharacters() },
                text = "Refresh",
                enabled = !viewModel.mudMobileBusy,
                modifier = Modifier.fillMaxWidth(),
            )
            val syncState by viewModel.syncState.collectAsState()
            WarlockOutlinedButton(
                onClick = { viewModel.syncSettings() },
                text = if (syncState.status == SyncStatus.Syncing) "Syncing settings…" else "Sync settings",
                enabled = syncState.status != SyncStatus.Syncing,
                modifier = Modifier.fillMaxWidth(),
            )
            syncState.message?.let { Text(it) }
            WarlockOutlinedButton(
                onClick = { viewModel.disconnectMudMobile() },
                text = "Disconnect MUD Mobile",
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Suppress("ktlint:compose:vm-forwarding-check")
@Composable
private fun MudMobileMenu(
    viewModel: DashboardViewModel,
    connected: Boolean,
    ui: DashboardUiState,
) {
    WarlockMenuButton(
        anchor = { toggle ->
            WarlockOutlinedButton(
                onClick = toggle,
                text = if (connected) "MUD Mobile ▾" else "MUD Mobile",
                enabled = !viewModel.mudMobileBusy,
            )
        },
    ) { dismiss ->
        if (!connected) {
            selectableItem(selected = false, onClick = {
                dismiss()
                ui.showTokenDialog = true
            }) { Text("Connect to MUD Mobile") }
        } else {
            selectableItem(selected = false, onClick = {
                dismiss()
                ui.showAddCharacterDialog = true
            }) { Text("Add a character") }
            selectableItem(selected = false, onClick = {
                dismiss()
                viewModel.refreshMudMobileCharacters()
            }) { Text("Refresh") }
            selectableItem(selected = false, onClick = {
                dismiss()
                viewModel.syncSettings()
            }) { Text("Sync settings") }
            selectableItem(selected = false, onClick = {
                dismiss()
                viewModel.disconnectMudMobile()
            }) { Text("Disconnect MUD Mobile") }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Connection list
// ---------------------------------------------------------------------------------------------

@Suppress("ktlint:compose:vm-forwarding-check")
@Composable
private fun ConnectionListPane(
    viewModel: DashboardViewModel,
    connections: List<StoredConnection>,
    mudMobileConnected: Boolean,
    ui: DashboardUiState,
) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Saved connections", fontWeight = FontWeight.SemiBold)
        if (connections.isEmpty()) {
            // Not first-run (handled earlier), so MUD Mobile is connected but has no characters yet.
            NoCharactersPointer()
        } else {
            ReorderableConnectionList(viewModel, connections, ui)
        }
    }
}

@Suppress("ktlint:compose:vm-forwarding-check")
@Composable
private fun ReorderableConnectionList(
    viewModel: DashboardViewModel,
    connections: List<StoredConnection>,
    ui: DashboardUiState,
) {
    WarlockScrollableColumn(
        modifier =
            Modifier.fillMaxSize().semantics {
                contentDescription = "List of stored connections"
            },
    ) {
        ReorderableColumn(
            list = connections,
            onSettle = { from, to ->
                val ids = connections.mapTo(mutableListOf()) { it.id }
                ids.add(to, ids.removeAt(from))
                viewModel.reorderConnections(ids)
            },
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) { _, connection, _ ->
            key(connection.id) {
                ReorderableItem(modifier = Modifier.fillMaxWidth()) {
                    ConnectionRow(
                        viewModel = viewModel,
                        connection = connection,
                        dragHandle = { DragHandle(modifier = Modifier.draggableHandle()) },
                        ui = ui,
                    )
                }
            }
        }
    }
}

@Composable
private fun DragHandle(modifier: Modifier = Modifier) {
    Icon(
        key = AllIconsKeys.General.Drag,
        contentDescription = "Drag to reorder",
        modifier =
            modifier
                .pointerHoverIcon(PointerIcon.Hand)
                .padding(horizontal = 6.dp, vertical = 4.dp),
    )
}

@Composable
private fun NoCharactersPointer(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .border(1.dp, JewelTheme.globalColors.borders.normal, RoundedCornerShape(8.dp))
                .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("No characters yet")
            Text(
                "Use \"Add a character\" above, or visit mudmobile.com to register one and it will appear here.",
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Suppress("ktlint:compose:vm-forwarding-check")
@Composable
private fun ConnectionRow(
    viewModel: DashboardViewModel,
    connection: StoredConnection,
    dragHandle: @Composable () -> Unit,
    ui: DashboardUiState,
) {
    val shape = RoundedCornerShape(8.dp)
    val stripeColor = if (connection.mudMobile) MudMobileAccent else JewelTheme.globalColors.borders.normal

    fun login() {
        // A connection logs in with its account's saved password; prompt to set one when it's
        // missing (both MUD Mobile and play.net logins need it) instead of attempting a doomed login.
        if (connection.password.isNullOrBlank()) {
            ui.passwordPrompt = connection
        } else {
            viewModel.connect(connection)
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .clip(shape)
                .background(JewelTheme.globalColors.panelBackground)
                .border(1.dp, JewelTheme.globalColors.borders.normal, shape)
                .combinedClickable(onClick = {}, onDoubleClick = { login() })
                .semantics { contentDescription = "Saved connection" },
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // Accent stripe (emerald for MUD Mobile, neutral otherwise).
            Box(modifier = Modifier.width(5.dp).fillMaxHeight().background(stripeColor))
            dragHandle()
            Row(
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WarlockButton(onClick = { login() }, text = "Login")
                Column(modifier = Modifier.weight(1f)) {
                    Text(connection.name, fontSize = 16.sp)
                    connectionSubline(connection)?.let {
                        Text(it, fontSize = 11.sp, color = JewelTheme.globalColors.text.info)
                    }
                }
                ConnectionRowMenu(connection = connection, ui = ui)
            }
        }
    }
}

@Composable
private fun ConnectionRowMenu(
    connection: StoredConnection,
    ui: DashboardUiState,
) {
    WarlockMenuButton(
        anchor = { toggle ->
            Text(
                "⋯",
                modifier =
                    Modifier
                        .clickable { toggle() }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        },
    ) { dismiss ->
        if (!connection.mudMobile) {
            selectableItem(selected = false, onClick = {
                dismiss()
                ui.editConnection = connection
            }) { Text("Edit") }
            selectableItem(selected = false, onClick = {
                dismiss()
                ui.deleteConnection = connection
            }) { Text("Delete") }
        } else {
            selectableItem(selected = false, onClick = {
                dismiss()
                ui.deleteConnection = connection
            }) { Text("Remove") }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// First run
// ---------------------------------------------------------------------------------------------

@Composable
private fun FirstRunPanel(
    onCreate: () -> Unit,
    onConnectMudMobile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Welcome to Warlock", fontWeight = FontWeight.SemiBold)
            Text("No connections yet. Get into a game one of two ways:")
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(
                    modifier =
                        Modifier
                            .width(220.dp)
                            .border(1.dp, JewelTheme.globalColors.borders.normal, RoundedCornerShape(8.dp))
                            .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("New character")
                    Text("Log in to Simutronics and pick a game.")
                    WarlockButton(onClick = onCreate, text = "Create a connection")
                }
                Column(
                    modifier =
                        Modifier
                            .width(220.dp)
                            .border(1.dp, MudMobileAccent, RoundedCornerShape(8.dp))
                            .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("MUD Mobile")
                    Text("Already have an account? Bring your cloud characters in.")
                    WarlockOutlinedButton(onClick = onConnectMudMobile, text = "Connect to MUD Mobile")
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Dialogs
// ---------------------------------------------------------------------------------------------

@Suppress("ktlint:compose:vm-forwarding-check")
@Composable
private fun DashboardDialogs(
    viewModel: DashboardViewModel,
    ui: DashboardUiState,
) {
    val syncState by viewModel.syncState.collectAsState()
    if (syncState.conflicts.isNotEmpty()) {
        DesktopSettingsSyncConflictDialog(
            conflicts = syncState.conflicts,
            onResolve = { path, resolution -> viewModel.resolveSyncConflict(path, resolution) },
            onDismiss = { viewModel.deferSyncConflicts() },
        )
    }

    if (ui.showTokenDialog) {
        MudMobileTokenDialog(
            viewModel = viewModel,
            onClose = { ui.showTokenDialog = false },
        )
    }

    if (ui.showAddCharacterDialog) {
        MudMobileAddCharacterDialog(
            savedAccounts = viewModel.savedAccounts,
            onDiscover = { account, password, gameCode ->
                viewModel.discoverMudMobileCharacters(account, password, gameCode)
                ui.showAddCharacterDialog = false
            },
            onDismiss = { ui.showAddCharacterDialog = false },
        )
    }

    ui.passwordPrompt?.let { connection ->
        if (connection.mudMobile) {
            ConnectionPasswordDialog(
                connection = connection,
                description = "Enter the play.net password for account \"${connection.username}\".",
                note = "Your password stays on this machine; it is never sent to MUD Mobile.",
                confirmText = "Play",
                onConnect = { password ->
                    viewModel.connectMudMobile(connection, password)
                    ui.passwordPrompt = null
                },
                onDismiss = { ui.passwordPrompt = null },
            )
        } else {
            ConnectionPasswordDialog(
                connection = connection,
                description = "No password is saved for account \"${connection.username}\". Enter it to log in.",
                note = "Your password is saved on this machine for next time.",
                confirmText = "Login",
                onConnect = { password ->
                    viewModel.updatePasswordAndConnect(connection, password)
                    ui.passwordPrompt = null
                },
                onDismiss = { ui.passwordPrompt = null },
            )
        }
    }

    ui.editConnection?.let { connection ->
        DesktopConnectionSettingsDialog(
            name = connection.name,
            windowTitle = connection.windowTitle,
            proxySettings = connection.proxySettings,
            updateName = { viewModel.renameConnection(connection.id, it) },
            updateWindowTitle = { viewModel.updateWindowTitle(connection.id, it) },
            updateProxySettings = { viewModel.updateProxySettings(connection.id, it) },
            closeDialog = { ui.editConnection = null },
        )
    }

    ui.deleteConnection?.let { connection ->
        DesktopConfirmationDialog(
            title = if (connection.mudMobile) "Remove character" else "Delete connection",
            text =
                if (connection.mudMobile) {
                    "Remove ${connection.character} from your MUD Mobile characters? " +
                        "This does not affect your play.net account."
                } else {
                    "Are you sure that you want to delete: ${connection.name}"
                },
            onDismiss = { ui.deleteConnection = null },
            onConfirm = {
                if (connection.mudMobile) {
                    viewModel.deleteMudMobileConnection(connection)
                } else {
                    viewModel.deleteConnection(connection.id)
                }
                ui.deleteConnection = null
            },
        )
    }

    // Unified connecting dialog: shown for both a direct login and a MUD Mobile login.
    if (viewModel.busy || viewModel.mudMobileConnecting) {
        val status =
            if (viewModel.mudMobileConnecting) viewModel.mudMobileConnectStatus else viewModel.message
        ConnectingDialog(
            status = status ?: "Connecting...",
            onCancel = {
                if (viewModel.mudMobileConnecting) viewModel.cancelMudMobileConnect() else viewModel.cancelConnect()
            },
        )
    }
}

@Suppress("ktlint:compose:vm-forwarding-check")
@Composable
private fun MudMobileTokenDialog(
    viewModel: DashboardViewModel,
    onClose: () -> Unit,
) {
    val tokenState = rememberTextFieldState("")
    var validating by remember { mutableStateOf(false) }
    var error: String? by remember { mutableStateOf(null) }
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()

    WarlockDialog(
        title = "Connect to MUD Mobile",
        onCloseRequest = { if (!validating) onClose() },
        width = 520.dp,
        height = 300.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Paste a device token from your MUD Mobile dashboard (Tokens tab). " +
                    "It starts with \"wlk_\".",
            )
            WarlockTextField(
                state = tokenState,
                modifier = Modifier.fillMaxWidth(),
                enabled = !validating,
                placeholder = "wlk_...",
            )
            when {
                validating -> Text("Validating...")
                error != null -> Text(error!!)
            }
            WarlockOutlinedButton(
                onClick = { uriHandler.openUri("https://mudmobile.com") },
                text = "Open mudmobile.com",
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                WarlockOutlinedButton(onClick = onClose, text = "Cancel", enabled = !validating)
                WarlockButton(
                    onClick = {
                        validating = true
                        error = null
                        scope.launch {
                            val result = viewModel.connectMudMobileToken(tokenState.text.toString())
                            validating = false
                            if (result == null) onClose() else error = result
                        }
                    },
                    text = "Save",
                    enabled = !validating,
                )
            }
        }
    }
}

@Composable
private fun MudMobileAddCharacterDialog(
    savedAccounts: List<AccountEntity>,
    onDiscover: (account: String, password: String, gameCode: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val firstAccount = savedAccounts.firstOrNull()
    val accountState = rememberTextFieldState(firstAccount?.username ?: "")
    val passwordState = rememberTextFieldState(firstAccount?.password ?: "")
    var gameCode by remember { mutableStateOf(MUD_MOBILE_GAME_CODES.first()) }

    WarlockDialog(
        title = "Add a character",
        onCloseRequest = onDismiss,
        width = 460.dp,
        height = 420.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Log in with SGE to discover your characters and add them to MUD Mobile.")
            Text("Your password stays on this machine; it is never sent to MUD Mobile.")
            Text("Account")
            if (savedAccounts.isEmpty()) {
                WarlockTextField(state = accountState, modifier = Modifier.fillMaxWidth())
            } else {
                WarlockEditableTextDropdown(
                    state = accountState,
                    items = savedAccounts,
                    modifier = Modifier.fillMaxWidth(),
                    itemLabel = { it.username },
                    onSelect = { account ->
                        accountState.setTextAndPlaceCursorAtEnd(account.username)
                        passwordState.setTextAndPlaceCursorAtEnd(account.password ?: "")
                    },
                )
            }
            Text("Password")
            WarlockPasswordField(state = passwordState, modifier = Modifier.fillMaxWidth())
            Text("Game")
            WarlockDropdownSelect(
                items = MUD_MOBILE_GAME_CODES,
                selected = gameCode,
                onSelect = { gameCode = it },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                WarlockOutlinedButton(onClick = onDismiss, text = "Cancel")
                WarlockButton(
                    onClick = {
                        onDiscover(
                            accountState.text.toString().trim(),
                            passwordState.text.toString(),
                            gameCode,
                        )
                    },
                    text = "Discover",
                )
            }
        }
    }
}

@Composable
private fun ConnectionPasswordDialog(
    connection: StoredConnection,
    description: String,
    confirmText: String,
    onConnect: (String) -> Unit,
    onDismiss: () -> Unit,
    note: String? = null,
) {
    val passwordState = rememberTextFieldState(connection.password ?: "")

    WarlockDialog(
        title = "Play ${connection.character}",
        onCloseRequest = onDismiss,
        width = 480.dp,
        height = 260.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(description)
            if (note != null) {
                Text(note)
            }
            WarlockPasswordField(state = passwordState, modifier = Modifier.fillMaxWidth())
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                WarlockOutlinedButton(onClick = onDismiss, text = "Cancel")
                WarlockButton(
                    onClick = { onConnect(passwordState.text.toString()) },
                    text = confirmText,
                    enabled = passwordState.text.isNotBlank(),
                )
            }
        }
    }
}

@Composable
private fun ConnectingDialog(
    status: String,
    onCancel: () -> Unit,
) {
    // Cancel-only: no close (X) action; closing must be a deliberate Cancel that tears down the session.
    WarlockDialog(
        title = "Connecting",
        onCloseRequest = {},
        width = 420.dp,
        height = 200.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(status)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                WarlockOutlinedButton(onClick = onCancel, text = "Cancel")
            }
        }
    }
}
