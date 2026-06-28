package warlockfe.warlock3.compose.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SecureTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import warlockfe.warlock3.compose.components.ConfirmationDialog
import warlockfe.warlock3.compose.components.DropdownSelect
import warlockfe.warlock3.compose.components.ScrollableLazyColumn
import warlockfe.warlock3.compose.generated.resources.Res
import warlockfe.warlock3.compose.generated.resources.add
import warlockfe.warlock3.compose.generated.resources.more_vert
import warlockfe.warlock3.core.mudmobile.ConflictResolution
import warlockfe.warlock3.core.mudmobile.SyncConflict
import warlockfe.warlock3.core.mudmobile.SyncStatus
import warlockfe.warlock3.core.prefs.models.AccountEntity
import warlockfe.warlock3.core.sge.StoredConnection

@Suppress("ktlint:compose:vm-forwarding-check")
@Composable
fun DashboardView(
    viewModel: DashboardViewModel,
    connectToSGE: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val connections by viewModel.connections.collectAsState(emptyList())
    val token by viewModel.mudMobileToken.collectAsState(null)
    val mudMobileConnected = token != null
    val syncState by viewModel.syncState.collectAsState()

    var showTokenDialog by remember { mutableStateOf(false) }
    var showAddCharacterDialog by remember { mutableStateOf(false) }
    var passwordPrompt: StoredConnection? by remember { mutableStateOf(null) }
    var editConnection: StoredConnection? by remember { mutableStateOf(null) }
    var deleteConnection: StoredConnection? by remember { mutableStateOf(null) }

    Surface(modifier) {
        if (connections.isEmpty() && !mudMobileConnected) {
            // First run: steer the user to one of two doors instead of an empty list.
            FirstRunPanel(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                createEnabled = !viewModel.busy,
                onCreate = connectToSGE,
                onConnectMudMobile = { showTokenDialog = true },
            )
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = connectToSGE,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                    enabled = !viewModel.busy,
                ) {
                    Icon(painter = painterResource(Res.drawable.add), contentDescription = null)
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(text = "Create a new connection")
                }

                MudMobileControls(
                    viewModel = viewModel,
                    connected = mudMobileConnected,
                    syncStatus = syncState.status,
                    onConnect = { showTokenDialog = true },
                    onAddCharacter = { showAddCharacterDialog = true },
                )

                viewModel.message?.takeIf { !viewModel.busy }?.let { Text(it) }
                syncState.message?.let { Text(it) }

                ConnectionList(
                    modifier = Modifier.weight(1f),
                    connections = connections,
                    mudMobileConnected = mudMobileConnected,
                    onLogin = { connection ->
                        // Prompt to set the account password when none is saved, rather than attempting
                        // a doomed empty-password login.
                        if (connection.password.isNullOrBlank()) {
                            passwordPrompt = connection
                        } else {
                            viewModel.connect(connection)
                        }
                    },
                    onEdit = { editConnection = it },
                    onDelete = { deleteConnection = it },
                )
            }
        }
    }

    if (showTokenDialog) {
        MudMobileTokenDialog(viewModel = viewModel, onClose = { showTokenDialog = false })
    }

    if (showAddCharacterDialog) {
        MudMobileAddCharacterDialog(
            savedAccounts = viewModel.savedAccounts,
            onDiscover = { account, password, gameCode ->
                viewModel.discoverMudMobileCharacters(account, password, gameCode)
                showAddCharacterDialog = false
            },
            onDismiss = { showAddCharacterDialog = false },
        )
    }

    passwordPrompt?.let { connection ->
        PasswordPromptDialog(
            connection = connection,
            description =
                if (connection.mudMobile) {
                    "Enter the play.net password for account \"${connection.username}\". " +
                        "It stays on this device; it is never sent to MUD Mobile."
                } else {
                    "No password is saved for account \"${connection.username}\". Enter it to log in."
                },
            confirmText = if (connection.mudMobile) "Play" else "Login",
            onConnect = { password ->
                if (connection.mudMobile) {
                    viewModel.connectMudMobile(connection, password)
                } else {
                    viewModel.updatePasswordAndConnect(connection, password)
                }
                passwordPrompt = null
            },
            onDismiss = { passwordPrompt = null },
        )
    }

    editConnection?.let { connection ->
        ConnectionSettingsDialog(
            name = connection.name,
            windowTitle = connection.windowTitle,
            updateName = { viewModel.renameConnection(connection.id, it) },
            updateWindowTitle = { viewModel.updateWindowTitle(connection.id, it) },
            closeDialog = { editConnection = null },
        )
    }

    deleteConnection?.let { connection ->
        ConfirmationDialog(
            title = if (connection.mudMobile) "Remove character" else "Delete connection",
            text =
                if (connection.mudMobile) {
                    "Remove ${connection.character} from your MUD Mobile characters? " +
                        "This does not affect your play.net account."
                } else {
                    "Are you sure that you want to delete: ${connection.name}"
                },
            onDismiss = { deleteConnection = null },
            onConfirm = {
                if (connection.mudMobile) {
                    viewModel.deleteMudMobileConnection(connection)
                } else {
                    viewModel.deleteConnection(connection.id)
                }
                deleteConnection = null
            },
        )
    }

    if (syncState.conflicts.isNotEmpty()) {
        SettingsSyncConflictDialog(
            conflicts = syncState.conflicts,
            onResolve = { path, resolution -> viewModel.resolveSyncConflict(path, resolution) },
            onDismiss = { viewModel.deferSyncConflicts() },
        )
    }

    // Unified connecting overlay: shown for both a direct login and a MUD Mobile login.
    if (viewModel.busy || viewModel.mudMobileConnecting) {
        val status = if (viewModel.mudMobileConnecting) viewModel.mudMobileConnectStatus else viewModel.message
        ConnectingDialog(
            status = status ?: "Connecting...",
            onCancel = {
                if (viewModel.mudMobileConnecting) viewModel.cancelMudMobileConnect() else viewModel.cancelConnect()
            },
        )
    }
}

@Composable
private fun FirstRunPanel(
    createEnabled: Boolean,
    onCreate: () -> Unit,
    onConnectMudMobile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Welcome to Warlock", style = MaterialTheme.typography.headlineSmall)
        Text(
            "No connections yet. Get into a game one of two ways:",
            style = MaterialTheme.typography.bodyLarge,
        )
        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("New character", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Log in to Simutronics and pick a game.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = onCreate, enabled = createEnabled) {
                    Text("Create a connection")
                }
            }
        }
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, MudMobileAccent),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("MUD Mobile", style = MaterialTheme.typography.titleMedium, color = MudMobileAccent)
                Text(
                    "Already have an account? Bring your cloud characters in.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(
                    onClick = onConnectMudMobile,
                    border = BorderStroke(1.dp, MudMobileAccent),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MudMobileAccent),
                ) {
                    Text("Connect to MUD Mobile")
                }
            }
        }
    }
}

@Suppress("ktlint:compose:vm-forwarding-check")
@Composable
private fun MudMobileControls(
    viewModel: DashboardViewModel,
    connected: Boolean,
    syncStatus: SyncStatus,
    onConnect: () -> Unit,
    onAddCharacter: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        viewModel.mudMobileMessage?.let { Text(it) }
        if (!connected) {
            OutlinedButton(
                onClick = onConnect,
                modifier = Modifier.fillMaxWidth(),
                enabled = !viewModel.mudMobileBusy,
                border = BorderStroke(1.dp, MudMobileAccent),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MudMobileAccent),
            ) {
                Text("Connect to MUD Mobile")
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "MUD Mobile",
                    style = MaterialTheme.typography.titleMedium,
                    color = MudMobileAccent,
                    modifier = Modifier.weight(1f),
                )
                var menuExpanded by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { menuExpanded = true }, enabled = !viewModel.mudMobileBusy) {
                        Icon(
                            painter = painterResource(Res.drawable.more_vert),
                            contentDescription = "MUD Mobile actions",
                        )
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("Add a character") },
                            onClick = {
                                menuExpanded = false
                                onAddCharacter()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Refresh characters") },
                            onClick = {
                                menuExpanded = false
                                viewModel.refreshMudMobileCharacters()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(if (syncStatus == SyncStatus.Syncing) "Syncing settings..." else "Sync settings") },
                            enabled = syncStatus != SyncStatus.Syncing,
                            onClick = {
                                menuExpanded = false
                                viewModel.syncSettings()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Disconnect MUD Mobile") },
                            onClick = {
                                menuExpanded = false
                                viewModel.disconnectMudMobile()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionList(
    connections: List<StoredConnection>,
    mudMobileConnected: Boolean,
    onLogin: (StoredConnection) -> Unit,
    onEdit: (StoredConnection) -> Unit,
    onDelete: (StoredConnection) -> Unit,
    modifier: Modifier = Modifier,
) {
    ScrollableLazyColumn(
        modifier.semantics {
            this.contentDescription = "List of stored connections"
        },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(
                modifier = Modifier.semantics { heading() },
                text = "Saved connections",
                style = MaterialTheme.typography.headlineMedium,
            )
            if (connections.isEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    if (mudMobileConnected) {
                        "No characters yet. Use \"Add a character\" in the MUD Mobile menu, " +
                            "or visit mudmobile.com to register one and it will appear here."
                    } else {
                        "There are currently no stored connections"
                    },
                )
            }
        }
        items(connections) { connection ->
            ConnectionRow(
                connection = connection,
                onLogin = { onLogin(connection) },
                onEdit = { onEdit(connection) },
                onDelete = { onDelete(connection) },
            )
        }
    }
}

@Composable
private fun ConnectionRow(
    connection: StoredConnection,
    onLogin: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Accent stripe: indigo for MUD Mobile, neutral otherwise (a color-independent cue paired with
    // the "MUD Mobile" subline text).
    val stripeColor = if (connection.mudMobile) MudMobileAccent else MaterialTheme.colorScheme.outline
    val subline = connectionSubline(connection)
    OutlinedCard(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(stripeColor),
            )
            Row(
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalButton(onClick = onLogin) {
                    Text("Login")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = connection.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (subline != null) {
                        Text(
                            text = subline,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                var menuExpanded by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            painter = painterResource(Res.drawable.more_vert),
                            contentDescription = "Connection actions",
                        )
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        // MUD Mobile connections are managed in the MUD Mobile dashboard, so only the
                        // play.net connections expose the settings editor.
                        if (!connection.mudMobile) {
                            DropdownMenuItem(
                                text = { Text("Edit") },
                                onClick = {
                                    menuExpanded = false
                                    onEdit()
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(if (connection.mudMobile) "Remove" else "Delete") },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            },
                        )
                    }
                }
            }
        }
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

    AlertDialog(
        onDismissRequest = { if (!validating) onClose() },
        title = { Text("Connect to MUD Mobile") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Paste a device token from your MUD Mobile dashboard (Tokens tab). It starts with \"wlk_\".",
                )
                TextField(
                    state = tokenState,
                    enabled = !validating,
                    lineLimits = TextFieldLineLimits.SingleLine,
                    label = { Text("Device token") },
                )
                when {
                    validating -> Text("Validating...")
                    error != null -> Text(error!!)
                }
                TextButton(onClick = { uriHandler.openUri("https://mudmobile.com") }) {
                    Text("Open mudmobile.com")
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !validating,
                onClick = {
                    validating = true
                    error = null
                    scope.launch {
                        val result = viewModel.connectMudMobileToken(tokenState.text.toString())
                        validating = false
                        if (result == null) onClose() else error = result
                    }
                },
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(enabled = !validating, onClick = onClose) {
                Text("Cancel")
            }
        },
    )
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
    var selectedAccount by remember { mutableStateOf(firstAccount) }
    var gameCode by remember { mutableStateOf(MUD_MOBILE_GAME_CODES.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a character") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Log in with SGE to discover your characters and add them to MUD Mobile.")
                Text("This login happens locally; your password never leaves this device.")
                if (savedAccounts.isEmpty()) {
                    TextField(
                        state = accountState,
                        label = { Text("Account") },
                        lineLimits = TextFieldLineLimits.SingleLine,
                    )
                } else {
                    DropdownSelect(
                        items = savedAccounts,
                        selected = selectedAccount ?: savedAccounts.first(),
                        onSelect = { account ->
                            selectedAccount = account
                            accountState.setTextAndPlaceCursorAtEnd(account.username)
                            passwordState.setTextAndPlaceCursorAtEnd(account.password ?: "")
                        },
                        itemLabelBuilder = { it.username },
                        label = { Text("Account") },
                    )
                }
                RevealPasswordField(state = passwordState, label = "Password")
                DropdownSelect(
                    items = MUD_MOBILE_GAME_CODES,
                    selected = gameCode,
                    onSelect = { gameCode = it },
                    label = { Text("Game") },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDiscover(accountState.text.toString().trim(), passwordState.text.toString(), gameCode)
                },
            ) {
                Text("Discover")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun PasswordPromptDialog(
    connection: StoredConnection,
    description: String,
    confirmText: String,
    onConnect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val passwordState = rememberTextFieldState(connection.password ?: "")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Play ${connection.character}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(description)
                RevealPasswordField(state = passwordState, label = "Password")
            }
        },
        confirmButton = {
            TextButton(
                enabled = passwordState.text.isNotBlank(),
                onClick = { onConnect(passwordState.text.toString()) },
            ) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

/** A password field with a Show/Hide toggle, matching the design's reveal affordance. */
@Composable
private fun RevealPasswordField(
    state: androidx.compose.foundation.text.input.TextFieldState,
    label: String,
    modifier: Modifier = Modifier,
) {
    var revealed by remember { mutableStateOf(false) }
    val toggle: @Composable () -> Unit = {
        TextButton(onClick = { revealed = !revealed }) {
            Text(if (revealed) "Hide" else "Show")
        }
    }
    if (revealed) {
        TextField(
            state = state,
            modifier = modifier,
            label = { Text(label) },
            lineLimits = TextFieldLineLimits.SingleLine,
            trailingIcon = toggle,
        )
    } else {
        SecureTextField(
            state = state,
            modifier = modifier,
            label = { Text(label) },
            trailingIcon = toggle,
        )
    }
}

@Composable
private fun ConnectingDialog(
    status: String,
    onCancel: () -> Unit,
) {
    // Non-dismissible: closing must be a deliberate Cancel that tears down the session.
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Connecting") },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator()
                Text(status)
            }
        },
        confirmButton = {
            TextButton(onClick = onCancel) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun SettingsSyncConflictDialog(
    conflicts: List<SyncConflict>,
    onResolve: (path: String, resolution: ConflictResolution) -> Unit,
    onDismiss: () -> Unit,
) {
    val conflict = conflicts.firstOrNull() ?: return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Resolve settings conflict") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(conflict.path, fontWeight = FontWeight.SemiBold)
                Text(
                    "This file changed on this device and on MUD Mobile. Choose which version to keep" +
                        (if (conflicts.size > 1) " (${conflicts.size} files need review)." else "."),
                )
                DiffPane(
                    title = "This device",
                    content = conflict.localContent,
                    deletedText = "Deleted on this device",
                )
                DiffPane(
                    title = "MUD Mobile" + (conflict.remoteModified?.let { " (updated $it)" } ?: ""),
                    content = conflict.remoteContent,
                    deletedText = "Deleted on MUD Mobile",
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onResolve(conflict.path, ConflictResolution.KEEP_LOCAL) }) {
                    Text("Keep this device's")
                }
                TextButton(onClick = { onResolve(conflict.path, ConflictResolution.TAKE_REMOTE) }) {
                    Text("Use MUD Mobile's")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Later")
            }
        },
    )
}

@Composable
private fun DiffPane(
    title: String,
    content: String?,
    deletedText: String,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(4.dp)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 160.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outline, shape)
                    .background(MaterialTheme.colorScheme.surfaceVariant, shape)
                    .padding(8.dp)
                    .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = content ?: deletedText,
                fontFamily = if (content == null) FontFamily.Default else FontFamily.Monospace,
            )
        }
    }
}
