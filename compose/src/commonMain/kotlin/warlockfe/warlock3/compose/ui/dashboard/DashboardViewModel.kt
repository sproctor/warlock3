package warlockfe.warlock3.compose.ui.dashboard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import warlockfe.warlock3.compose.ConnectToGameUseCase
import warlockfe.warlock3.compose.MudMobileConnectResult
import warlockfe.warlock3.compose.MudMobileConnectUseCase
import warlockfe.warlock3.compose.MudMobileDiscoverResult
import warlockfe.warlock3.compose.MudMobileDiscoverUseCase
import warlockfe.warlock3.compose.model.GameScreen
import warlockfe.warlock3.compose.model.GameState
import warlockfe.warlock3.core.mudmobile.CharactersResult
import warlockfe.warlock3.core.mudmobile.ConflictResolution
import warlockfe.warlock3.core.mudmobile.MudMobileApi
import warlockfe.warlock3.core.mudmobile.WarlockSettingsSync
import warlockfe.warlock3.core.prefs.models.AccountEntity
import warlockfe.warlock3.core.prefs.repositories.AccountRepository
import warlockfe.warlock3.core.prefs.repositories.ClientSettingRepository
import warlockfe.warlock3.core.prefs.repositories.ConnectionRepository
import warlockfe.warlock3.core.prefs.repositories.ConnectionSettingsRepository
import warlockfe.warlock3.core.sge.AutoConnectResult
import warlockfe.warlock3.core.sge.ConnectionProxySettings
import warlockfe.warlock3.core.sge.SgeClientFactory
import warlockfe.warlock3.core.sge.SgeSettings
import warlockfe.warlock3.core.sge.StoredConnection

class DashboardViewModel(
    private val gameState: GameState,
    private val sgeSettings: SgeSettings,
    private val connectionRepository: ConnectionRepository,
    private val connectionSettingsRepository: ConnectionSettingsRepository,
    private val sgeClientFactory: SgeClientFactory,
    private val connectToGame: ConnectToGameUseCase,
    private val clientSettingRepository: ClientSettingRepository,
    private val accountRepository: AccountRepository,
    private val mudMobileApi: MudMobileApi,
    private val mudMobileConnect: MudMobileConnectUseCase,
    private val mudMobileDiscover: MudMobileDiscoverUseCase,
    private val warlockSettingsSync: WarlockSettingsSync,
) : ViewModel() {
    val connections = connectionRepository.observeAllConnections()

    /** Settings-sync status + any conflicts awaiting the user's decision. */
    val syncState = warlockSettingsSync.state

    private val logger = Logger.withTag("DashboardViewModel")

    var busy by mutableStateOf(false)
        private set

    var message: String? by mutableStateOf(null)
        private set

    private var connectJob: Job? = null

    // --- MUD Mobile state ---

    val mudMobileToken: Flow<String?> = clientSettingRepository.observeMudMobileToken()

    // Inline status for the MUD Mobile section (token validation / character loading), separate from
    // the heavy game-connect spinner that reuses [busy]/[message].
    var mudMobileBusy by mutableStateOf(false)
        private set

    var mudMobileMessage: String? by mutableStateOf(null)
        private set

    // Connect-in-progress state: drives the status dialog (with its Cancel button) shown while a
    // MUD Mobile session is being started and waited on.
    var mudMobileConnecting by mutableStateOf(false)
        private set

    var mudMobileConnectStatus: String? by mutableStateOf(null)
        private set

    private var mudMobileSessionId: String? = null

    // Saved play.net accounts, used to pre-fill passwords and offer an account dropdown in the
    // MUD Mobile dialogs.
    var savedAccounts: List<AccountEntity> by mutableStateOf(emptyList())
        private set

    init {
        viewModelScope.launch {
            reloadAccounts()
        }
        // Load the saved characters if the user already connected their MUD Mobile account.
        viewModelScope.launch {
            if (clientSettingRepository.getMudMobileToken() != null) {
                refreshMudMobileCharacters()
                // Auto-sync settings whenever the dashboard appears (app start, and on return from a
                // game after disconnect) so per-character settings follow the user between machines.
                warlockSettingsSync.sync()
            }
        }
        maybeAutoConnectLastConnection()
    }

    // --- Settings sync ---

    /** Manually trigger a settings sync (the "Sync now" action). */
    fun syncSettings() {
        viewModelScope.launch { warlockSettingsSync.sync() }
    }

    /** Resolve one conflicting file with the user's choice (keep local / take remote). */
    fun resolveSyncConflict(
        path: String,
        resolution: ConflictResolution,
    ) {
        viewModelScope.launch { warlockSettingsSync.resolveConflict(path, resolution) }
    }

    fun dismissSyncMessage() {
        warlockSettingsSync.clearMessage()
    }

    /** Defer the pending conflicts ("Later"); they resurface on the next sync. */
    fun deferSyncConflicts() {
        warlockSettingsSync.clearConflicts()
    }

    private suspend fun reloadAccounts() {
        savedAccounts = accountRepository.getAll()
    }

    /** The saved password for a play.net account, if Warlock has one. */
    fun savedPasswordFor(username: String): String? =
        savedAccounts.firstOrNull { it.username.equals(username, ignoreCase = true) }?.password

    /**
     * On the first dashboard shown this session, reconnect the last launched connection if the user
     * enabled auto-connect and that connection still exists. Guarded by
     * [GameState.autoConnectAttempted] so it fires at most once per window (not again when returning
     * to the dashboard after a disconnect).
     */
    private fun maybeAutoConnectLastConnection() {
        if (gameState.autoConnectAttempted) return
        gameState.autoConnectAttempted = true
        viewModelScope.launch {
            if (!clientSettingRepository.getAutoConnectLastConnection()) return@launch
            val lastConnectionId = clientSettingRepository.getLastConnectionId() ?: return@launch
            val connection = connectionRepository.getById(lastConnectionId) ?: return@launch
            connect(connection)
        }
    }

    fun connect(connection: StoredConnection) {
        // MUD Mobile connections route through the hosted-Lich flow instead of a direct play.net login.
        if (connection.mudMobile) {
            connectMudMobile(connection, connection.password ?: "")
            return
        }
        if (busy) return
        busy = true
        connectJob?.cancel()
        connectJob =
            viewModelScope.launch {
                try {
                    // Remember this as the last launched connection (for auto-connect-on-startup).
                    clientSettingRepository.putLastConnectionId(connection.id)
                    message = "Connecting..."
                    val sgeClient = sgeClientFactory.create()
                    val result = sgeClient.autoConnect(sgeSettings, connection)
                    sgeClient.close()
                    when (result) {
                        is AutoConnectResult.Failure -> {
                            message = result.reason
                        }

                        is AutoConnectResult.Success -> {
                            connectToGame(result.credentials, connection.proxySettings, gameState)
                        }
                    }
                } catch (e: Exception) {
                    ensureActive()
                    logger.e(e) { "Error connecting to server" }
                    gameState.setScreen(
                        GameScreen.ErrorState(
                            message = "Error: ${e.message}",
                            returnTo = GameScreen.Dashboard,
                        ),
                    )
                } finally {
                    connectJob = null
                    busy = false
                }
            }
    }

    fun cancelConnect() {
        connectJob?.cancel()
        connectJob = null
        message = null
    }

    // --- MUD Mobile actions ---

    /**
     * Validate and store a freshly-pasted device token, then load the user's characters. Returns
     * null on success, or an error message to show inline in the token dialog. Suspends so the
     * dialog can show its own "Validating..." state and keep itself open on failure.
     */
    suspend fun connectMudMobileToken(token: String): String? {
        val trimmed = token.trim()
        if (trimmed.isEmpty()) return "Paste a token first."
        return when (val result = mudMobileApi.getCharacters(trimmed)) {
            is CharactersResult.Success -> {
                clientSettingRepository.putMudMobileToken(trimmed)
                connectionRepository.syncMudMobileConnections(result.characters)
                mudMobileMessage = null
                // Pull/push settings now that the account is connected.
                viewModelScope.launch { warlockSettingsSync.sync() }
                null
            }

            CharactersResult.Unauthorized -> {
                "That token was rejected. Check it and paste it again."
            }

            is CharactersResult.Error -> {
                "Couldn't reach MUD Mobile: ${result.message}"
            }
        }
    }

    fun refreshMudMobileCharacters() {
        if (mudMobileBusy) return
        mudMobileBusy = true
        viewModelScope.launch {
            try {
                val token = clientSettingRepository.getMudMobileToken() ?: return@launch
                when (val result = mudMobileApi.getCharacters(token)) {
                    is CharactersResult.Success -> {
                        connectionRepository.syncMudMobileConnections(result.characters)
                        mudMobileMessage = null
                    }

                    CharactersResult.Unauthorized -> {
                        mudMobileMessage = "Your MUD Mobile token is no longer valid. Reconnect your account."
                    }

                    is CharactersResult.Error -> {
                        mudMobileMessage = "Couldn't reach MUD Mobile: ${result.message}"
                    }
                }
            } finally {
                mudMobileBusy = false
            }
        }
    }

    /**
     * Discover the user's characters for a game via a local SGE login and register them with MUD
     * Mobile, then reload the list. Used to populate an empty picker from Warlock.
     */
    fun discoverMudMobileCharacters(
        account: String,
        password: String,
        gameCode: String,
    ) {
        if (mudMobileBusy) return
        mudMobileBusy = true
        mudMobileMessage = "Discovering characters..."
        viewModelScope.launch {
            try {
                val token = clientSettingRepository.getMudMobileToken()
                if (token == null) {
                    mudMobileMessage = "Connect your MUD Mobile account first."
                    return@launch
                }
                // Remember the account so future logins can pre-fill its password.
                if (password.isNotEmpty()) {
                    accountRepository.save(AccountEntity(account.trim(), password))
                    reloadAccounts()
                }
                when (
                    val result =
                        mudMobileDiscover(
                            token = token,
                            sgeSettings = sgeSettings,
                            account = account.trim(),
                            password = password,
                            gameCode = gameCode,
                        )
                ) {
                    is MudMobileDiscoverResult.Success -> {
                        mudMobileMessage = "Added ${result.added} character(s)."
                    }

                    is MudMobileDiscoverResult.Failure -> {
                        mudMobileMessage = result.message
                    }
                }
            } finally {
                mudMobileBusy = false
            }
            // Reload the list to reflect whatever was registered.
            refreshMudMobileCharacters()
        }
    }

    /** Remove a MUD Mobile connection from MUD Mobile (and the local registry). */
    fun deleteMudMobileConnection(connection: StoredConnection) {
        viewModelScope.launch {
            val token = clientSettingRepository.getMudMobileToken()
            // The connection id is the MUD Mobile character id (see syncMudMobileConnections).
            if (token != null && !mudMobileApi.deleteCharacter(token, connection.id)) {
                mudMobileMessage = "Couldn't remove ${connection.character}."
                return@launch
            }
            connectionRepository.deleteConnection(connection.id)
        }
    }

    /** Forget the stored token and drop the MUD Mobile connections from the list. */
    fun disconnectMudMobile() {
        viewModelScope.launch {
            clientSettingRepository.putMudMobileToken(null)
            connectionRepository.removeMudMobileConnections()
            mudMobileMessage = null
        }
    }

    fun connectMudMobile(
        connection: StoredConnection,
        password: String,
    ) {
        if (mudMobileConnecting) return
        mudMobileConnecting = true
        mudMobileConnectStatus = "Starting..."
        mudMobileMessage = null
        mudMobileSessionId = null
        connectJob?.cancel()
        connectJob =
            viewModelScope.launch {
                try {
                    // Remember this as the last launched connection (for auto-connect-on-startup).
                    clientSettingRepository.putLastConnectionId(connection.id)
                    val token = clientSettingRepository.getMudMobileToken()
                    if (token == null) {
                        mudMobileMessage = "Connect your MUD Mobile account first."
                        return@launch
                    }
                    // Remember the account password for next time.
                    if (password.isNotEmpty()) {
                        accountRepository.save(AccountEntity(connection.username, password))
                        reloadAccounts()
                    }
                    val result =
                        mudMobileConnect(
                            token = token,
                            sgeSettings = sgeSettings,
                            connection = connection,
                            password = password,
                            gameState = gameState,
                            onStatus = { mudMobileConnectStatus = it },
                            onSessionCreated = { mudMobileSessionId = it },
                        )
                    if (result is MudMobileConnectResult.Failure) {
                        mudMobileMessage = result.message
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    ensureActive()
                    logger.e(e) { "Error connecting to MUD Mobile" }
                    mudMobileMessage = "Error: ${e.message}"
                } finally {
                    connectJob = null
                    mudMobileConnecting = false
                    mudMobileConnectStatus = null
                }
            }
    }

    /** Cancel an in-progress MUD Mobile connect and tear down the half-created session. */
    fun cancelMudMobileConnect() {
        connectJob?.cancel()
        connectJob = null
        mudMobileConnecting = false
        mudMobileConnectStatus = null
        val sessionId = mudMobileSessionId
        mudMobileSessionId = null
        if (sessionId != null) {
            viewModelScope.launch {
                clientSettingRepository.getMudMobileToken()?.let { token ->
                    mudMobileApi.deleteSession(token, sessionId)
                }
            }
        }
    }

    fun updateProxySettings(
        characterId: String,
        proxySettings: ConnectionProxySettings,
    ) {
        viewModelScope.launch {
            connectionSettingsRepository.saveProxySettings(characterId, proxySettings)
        }
    }

    fun renameConnection(
        id: String,
        newName: String,
    ) {
        viewModelScope.launch {
            connectionRepository.renameById(id, newName)
        }
    }

    fun updateWindowTitle(
        id: String,
        windowTitle: String?,
    ) {
        viewModelScope.launch {
            connectionRepository.saveWindowTitle(id, windowTitle)
        }
    }

    /** Persist a new ordering of the saved-connections list (after a drag-to-reorder). */
    fun reorderConnections(orderedIds: List<String>) {
        viewModelScope.launch {
            connectionRepository.reorderConnections(orderedIds)
        }
    }

    fun deleteConnection(id: String) {
        viewModelScope.launch {
            connectionRepository.deleteConnection(id)
        }
    }
}
