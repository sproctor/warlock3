package warlockfe.warlock3.compose

import co.touchlab.kermit.Logger
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import warlockfe.warlock3.compose.ui.window.WindowRegistryFactory
import warlockfe.warlock3.core.client.WarlockClient
import warlockfe.warlock3.core.window.WindowRegistry

/**
 * A game connection, dialed and ready to play: a client on an open socket, the window registry it
 * draws into, and the key that starts it.
 *
 * @param reconnect dials the same game again and starts it the same way, or null when there is
 *   nothing to redial (a replayed log).
 */
class GameConnection(
    val client: WarlockClient,
    val windowRegistry: WindowRegistry,
    val key: String,
    val reconnect: (suspend () -> GameConnectResult)?,
)

/**
 * Starts a dialed [GameConnection]: in the app, puts its game on a window's screen.
 *
 * The connect use cases dial, and hand what they dialed to a starter rather than returning it.
 * Their caller supplies the starter, so a use case knows nothing of screens or view models, and the
 * handoff happens inside the use case, where nothing can cancel it between dialing and starting and
 * leave the socket open with no one to close it.
 *
 * [start] owns the connection from the moment it is called, and closes it if the game doesn't start.
 */
fun interface GameStarter {
    suspend fun start(connection: GameConnection)
}

/**
 * How a connect use case ended.
 *
 * Where a failure is shown is the caller's choice. A game server dialed with a key from a login
 * (or the command line) shows it on the error screen; a connect the dashboard makes and waits on
 * itself (a telnet MUD, MUD Mobile, a replayed log) reports it in the dashboard's error dialog.
 */
sealed interface GameConnectResult {
    /** Dialed, and handed to the [GameStarter]. */
    data object Started : GameConnectResult

    /** Could not dial; [message] says why, for the user. */
    data class Failure(
        val message: String,
    ) : GameConnectResult
}

/** How one attempt of [dialAndStart] went. */
internal sealed interface StartAttempt {
    data object Started : StartAttempt

    /** The server could not be reached. */
    data class DialFailed(
        val cause: Exception,
    ) : StartAttempt

    /** The server was reached, but the game did not start. */
    data class StartFailed(
        val cause: Exception,
    ) : StartAttempt
}

/** This attempt as the user hears of it, naming the server at [address] ("host:port"). */
internal fun StartAttempt.toResult(address: String): GameConnectResult =
    when (this) {
        StartAttempt.Started -> GameConnectResult.Started
        is StartAttempt.DialFailed -> GameConnectResult.Failure("Could not connect to $address: ${cause.reason}")
        is StartAttempt.StartFailed -> GameConnectResult.Failure("Connected to $address, but the game didn't start: ${cause.reason}")
    }

internal val Exception.reason: String
    get() = message ?: this::class.simpleName ?: "unknown error"

private val logger = Logger.withTag("GameConnection")

/**
 * Dials a game with [dial], builds its client with [createClient] and hands both to [starter]:
 * the one way every connect use case starts a game.
 *
 * Until the handoff the socket, and the window registry made here, are ours: whatever fails or
 * cancels before it closes them through [closeSocket] and the registry, so a failed attempt leaves
 * nothing open. From the handoff on they are the starter's. A failure to dial and a failure to
 * start come back apart, since only the first means the server wasn't there.
 *
 * @param closeSocket closes the socket; it may be called on a socket a refused dial already closed.
 */
internal suspend fun dialAndStart(
    windowRegistryFactory: WindowRegistryFactory,
    dial: suspend () -> Unit,
    closeSocket: () -> Unit,
    createClient: (WindowRegistry) -> WarlockClient,
    key: String,
    reconnect: (suspend () -> GameConnectResult)?,
    starter: GameStarter,
): StartAttempt {
    var registry: WindowRegistry? = null
    var handedOff = false
    var dialed = false
    try {
        dial()
        dialed = true
        // Made once the socket is up, so a refused attempt doesn't make one at all.
        val windowRegistry = windowRegistryFactory.create().also { registry = it }
        val client = createClient(windowRegistry)
        handedOff = true
        starter.start(GameConnection(client, windowRegistry, key, reconnect))
        return StartAttempt.Started
    } catch (e: Exception) {
        currentCoroutineContext().ensureActive()
        return if (dialed) {
            logger.e(e) { "The game didn't start" }
            StartAttempt.StartFailed(e)
        } else {
            logger.d(e) { "Could not connect" }
            StartAttempt.DialFailed(e)
        }
    } finally {
        if (!handedOff) {
            closeSocket()
            registry?.close()
        }
    }
}
