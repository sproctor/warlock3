package warlockfe.warlock3.compose

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

/** How a connect use case ended. */
sealed interface GameConnectResult {
    /** Dialed, and handed to the [GameStarter]. */
    data object Started : GameConnectResult

    /** Could not dial; [message] says why, for the user. */
    data class Failure(
        val message: String,
    ) : GameConnectResult
}
