package warlockfe.warlock3.compose

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import warlockfe.warlock3.compose.model.GameScreen
import warlockfe.warlock3.compose.model.GameState
import warlockfe.warlock3.compose.ui.game.GameViewModelFactory
import warlockfe.warlock3.compose.ui.window.WindowRegistryFactory
import warlockfe.warlock3.core.client.WarlockClient
import warlockfe.warlock3.core.client.WarlockClientFactory
import warlockfe.warlock3.core.mudmobile.CreateSessionResult
import warlockfe.warlock3.core.mudmobile.MudMobileApi
import warlockfe.warlock3.core.mudmobile.SessionConnectInfo
import warlockfe.warlock3.core.mudmobile.SessionStatusResult
import warlockfe.warlock3.core.sge.AutoConnectResult
import warlockfe.warlock3.core.sge.ConnectionProxySettings
import warlockfe.warlock3.core.sge.SgeClientFactory
import warlockfe.warlock3.core.sge.SgeSettings
import warlockfe.warlock3.core.sge.SimuGameCredentials
import warlockfe.warlock3.core.sge.StoredConnection
import warlockfe.warlock3.core.util.sha256Hex
import warlockfe.warlock3.wrayth.network.NetworkSocket
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Drives a MUD Mobile connection end to end:
 *  1. Run Warlock's normal local SGE/EAccess login (password stays on this machine).
 *  2. Hash the resulting game key and register a hosted-Lich session with MUD Mobile
 *     (`POST /api/sessions`) — only the hash leaves the machine.
 *  3. Open the game socket to the router MUD Mobile returns (TLS) and send the usual Stormfront
 *     handshake, then hand off to a normal [GameScreen.ConnectedGameState].
 *
 * See docs/specs/mudmobile-integration.md.
 */
class MudMobileConnectUseCase(
    private val api: MudMobileApi,
    private val sgeClientFactory: SgeClientFactory,
    private val warlockClientFactory: WarlockClientFactory,
    private val windowRegistryFactory: WindowRegistryFactory,
    private val gameViewModelFactory: GameViewModelFactory,
    private val ioDispatcher: CoroutineDispatcher,
) {
    private val logger = Logger.withTag("MudMobileConnect")

    suspend operator fun invoke(
        token: String,
        sgeSettings: SgeSettings,
        connection: StoredConnection,
        password: String,
        gameState: GameState,
        onStatus: (String) -> Unit = {},
        onSessionCreated: (String) -> Unit = {},
    ): MudMobileConnectResult {
        // 1. Local SGE — reuse the existing auto-connect flow, matching by character name.
        onStatus("Logging in to SGE...")
        val credentials =
            when (val sge = runSge(sgeSettings, connection, password)) {
                is AutoConnectResult.Failure -> return MudMobileConnectResult.Failure(sge.reason)
                is AutoConnectResult.Success -> sge.credentials
            }

        // Refresh the saved profile (idempotent upsert; keeps last-used current). Best-effort, and
        // only possible when we know the EAccess character code.
        connection.characterCode?.let { characterCode ->
            api.createCharacter(
                token = token,
                account = connection.username,
                game = connection.code,
                characterCode = characterCode,
                characterName = connection.character,
            )
        }

        // 2. Register the session. Only the key hash is sent — never the password or the raw key.
        onStatus("Starting your cloud session...")
        val keyHash = sha256Hex(credentials.key)
        val sessionId: String
        val connect =
            when (
                val result =
                    api.createSession(
                        token = token,
                        game = connection.code,
                        character = connection.character,
                        gamehost = credentials.host,
                        gameport = credentials.port,
                        keyHash = keyHash,
                    )
            ) {
                is CreateSessionResult.Success -> {
                    sessionId = result.sessionId
                    onSessionCreated(result.sessionId)
                    result.connect
                }

                CreateSessionResult.Unauthorized -> {
                    return MudMobileConnectResult.Failure(
                        "Your MUD Mobile token is no longer valid. Reconnect your MUD Mobile account.",
                    )
                }

                CreateSessionResult.SubscriptionRequired -> {
                    return MudMobileConnectResult.Failure(
                        "A MUD Mobile subscription is required. Visit mudmobile.com to subscribe.",
                    )
                }

                is CreateSessionResult.ConcurrentLimitReached -> {
                    return MudMobileConnectResult.Failure(
                        "You're already at your MUD Mobile session limit" +
                            (result.active?.let { a -> result.limit?.let { l -> " ($a/$l active)" } } ?: "") +
                            ". End an existing session and try again.",
                    )
                }

                is CreateSessionResult.Error -> {
                    return MudMobileConnectResult.Failure("Couldn't start session: ${result.message}")
                }
            }

        // 3. Wait for the session to announce it's ready before dialing the router. (Per the spec
        // the session is already routable at this point, so we only wait briefly: a warning at
        // WARN_AFTER, then connect regardless at FORCE_CONNECT_AFTER even without a ready signal.)
        onStatus("Waiting for the session to be ready...")
        when (val readiness = awaitReady(token, sessionId, onStatus)) {
            ReadinessResult.Ready -> Unit

            // proceed to connect
            is ReadinessResult.Failed -> return MudMobileConnectResult.Failure(readiness.reason)
        }

        // 4. Connect to the router and play. The hosted session is already routable here, so this is
        // one attempt with a generous timeout (the router holds the connection through the cloud
        // machine's cold boot, ~25-60s).
        onStatus("Connecting to the game...")
        return connectToRouter(connect, credentials, gameState)
    }

    /**
     * Open the game socket to the router and hand off to a [GameScreen.ConnectedGameState]. Used for
     * both the initial connect and reconnects.
     *
     * A reconnect re-dials this same router endpoint with the same game key - no SGE login and no new
     * session. The hosted session persists across a client disconnect, and the router still bridges
     * any connection whose sha256(key) matches what [invoke] registered, so re-dialing simply
     * re-attaches to the live session.
     */
    private suspend fun connectToRouter(
        connect: SessionConnectInfo,
        credentials: SimuGameCredentials,
        gameState: GameState,
    ): MudMobileConnectResult =
        withContext(ioDispatcher) {
            // Tracks the client until it's handed off to the GameViewModel. If we're cancelled (the
            // user returned to the dashboard mid-reconnect) or fail before that handoff, the finally
            // closes it so we don't leak the socket and the hosted session it holds open.
            var createdClient: WarlockClient? = null
            try {
                val streamRegistry = windowRegistryFactory.create()
                val socket = NetworkSocket(ioDispatcher, secure = connect.tls)
                socket.connect(connect.host, connect.port)
                val client =
                    warlockClientFactory.createClient(
                        windowRegistry = streamRegistry,
                        socket = socket,
                    )
                createdClient = client
                // Same handshake bytes Warlock sends to play.net; the router matches sha256(key).
                client.connect(credentials.key)
                val gameViewModel =
                    gameViewModelFactory.create(
                        client = client,
                        windowRegistry = streamRegistry,
                        // Reconnect re-dials the same router with the same key; no SGE, no new session.
                        reconnect = {
                            val result = connectToRouter(connect, credentials, gameState)
                            if (result is MudMobileConnectResult.Failure) {
                                gameState.setScreen(
                                    GameScreen.ErrorState(result.message, returnTo = GameScreen.Dashboard),
                                )
                            }
                        },
                    )
                gameState.setScreen(GameScreen.ConnectedGameState(gameViewModel))
                // Ownership has passed to the GameViewModel; don't tear it down in finally.
                createdClient = null
                MudMobileConnectResult.Success
            } catch (e: Exception) {
                ensureActive()
                logger.e(e) { "Failed to connect to MUD Mobile router" }
                MudMobileConnectResult.Failure("Error connecting to ${connect.host}:${connect.port}: ${e.message}")
            } finally {
                createdClient?.close()
            }
        }

    /**
     * Polls `GET /api/sessions/{id}`, surfacing each status update via [onStatus]. Returns as soon
     * as the session reports `ready`; warns at [WARN_AFTER] that we'll connect anyway, and at
     * [FORCE_CONNECT_AFTER] returns [ReadinessResult.Ready] regardless of the ready signal (the
     * session is already routable per the spec). Cancellation (the user pressing Cancel)
     * propagates out normally.
     */
    private suspend fun awaitReady(
        token: String,
        sessionId: String,
        onStatus: (String) -> Unit,
    ): ReadinessResult {
        val start = TimeSource.Monotonic.markNow()
        while (true) {
            when (val status = api.getSession(token, sessionId)) {
                is SessionStatusResult.Success -> {
                    if (status.ready) return ReadinessResult.Ready
                    when (status.status) {
                        "failed" -> return ReadinessResult.Failed("The cloud session failed to start.")
                        "ended" -> return ReadinessResult.Failed("The session ended before it was ready.")
                    }
                    val elapsed = start.elapsedNow()
                    when {
                        elapsed >= FORCE_CONNECT_AFTER -> {
                            onStatus("Session never reported ready; connecting anyway...")
                            return ReadinessResult.Ready
                        }

                        elapsed >= WARN_AFTER -> {
                            onStatus("Session isn't ready yet; connecting anyway shortly...")
                        }

                        else -> {
                            status.statusDetail?.let { onStatus(it) }
                                ?: status.status?.let { onStatus("Session status: $it") }
                        }
                    }
                }

                SessionStatusResult.Unauthorized -> {
                    return ReadinessResult.Failed(
                        "Your MUD Mobile token is no longer valid. Reconnect your account.",
                    )
                }

                SessionStatusResult.NotFound -> {
                    return ReadinessResult.Failed("The session no longer exists.")
                }

                // Transient network error: keep polling, but still honor the force-connect deadline.
                is SessionStatusResult.Error -> {
                    if (start.elapsedNow() >= FORCE_CONNECT_AFTER) {
                        onStatus("Session never reported ready; connecting anyway...")
                        return ReadinessResult.Ready
                    }
                }
            }
            delay(POLL_INTERVAL)
        }
    }

    private sealed interface ReadinessResult {
        data object Ready : ReadinessResult

        data class Failed(
            val reason: String,
        ) : ReadinessResult
    }

    private suspend fun runSge(
        sgeSettings: SgeSettings,
        connection: StoredConnection,
        password: String,
    ): AutoConnectResult {
        val sgeClient = sgeClientFactory.create()
        return try {
            // SGE auto-connect matches the character by name; proxying never applies on this path.
            sgeClient.autoConnect(
                settings = sgeSettings,
                connection =
                    connection.copy(
                        password = password,
                        proxySettings = ConnectionProxySettings(enabled = false, launchCommand = null, host = null, port = null),
                    ),
            )
        } finally {
            sgeClient.close()
        }
    }

    private companion object {
        val WARN_AFTER = 25.seconds
        val FORCE_CONNECT_AFTER = 30.seconds
        val POLL_INTERVAL = 1.seconds
    }
}

sealed interface MudMobileConnectResult {
    data object Success : MudMobileConnectResult

    data class Failure(
        val message: String,
    ) : MudMobileConnectResult
}
