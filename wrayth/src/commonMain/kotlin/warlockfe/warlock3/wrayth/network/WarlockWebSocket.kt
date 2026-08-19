package warlockfe.warlock3.wrayth.network

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.availableForRead
import io.ktor.utils.io.writeFully
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.launch
import kotlinx.io.IOException
import warlockfe.warlock3.core.client.WarlockSocket
import warlockfe.warlock3.core.util.encodeWindows1252
import kotlin.concurrent.Volatile

/**
 * A [WarlockSocket] that carries the game stream over a WebSocket instead of a raw TCP socket.
 *
 * The MUD Mobile router serves both on the same port, and the protocol above the transport is
 * identical: the first line is the game key, then bytes flow untouched in both directions. So
 * everything above this class — the handshake, the parser, the client — is the same code either
 * way. WebSocket is what iOS and a browser can actually reach, which is why the MUD Mobile path
 * uses it rather than [NetworkSocket].
 *
 * [httpClient] must have the `WebSockets` plugin installed. Do not wrap this in a reconnect loop:
 * after the key line the router holds the connection open while the cloud machine boots (~25-60s,
 * up to ~105s), and reconnecting fights that hold.
 */
class WarlockWebSocket(
    private val httpClient: HttpClient,
    dispatcher: CoroutineDispatcher,
    // False only for the router's plaintext endpoint (ws://); the API normally hands us tls=true.
    private val secure: Boolean = true,
) : WarlockSocket {
    private val logger = Logger.withTag("WarlockWebSocket")
    private val scope = CoroutineScope(dispatcher + SupervisorJob())

    // Frames are a transport detail, never a message boundary: the game is one byte stream, so
    // every frame's payload is appended here and read back exactly as a TCP stream is.
    private val receiveChannel = ByteChannel(autoFlush = true)
    private val buffer = ByteArray(4096)

    // Written on whichever thread closes the socket and read from the frame pump and the client's
    // read loop, so both are published rather than left to be seen whenever.
    @Volatile
    private var session: DefaultClientWebSocketSession? = null

    @Volatile
    private var closed = false

    // Only our own close counts, exactly as it does for a TCP socket. The peer going away is not
    // reported here: a client reads until a read comes back null and calls its disconnected handler
    // then, so a socket that reported itself closed the moment the stream ran dry would end that
    // loop through its `while (!isClosed)` guard instead, and the disconnect would go unannounced.
    override val isClosed: Boolean
        get() = closed

    override suspend fun connect(
        host: String,
        port: Int,
    ) {
        val url = "${if (secure) "wss" else "ws"}://$host:$port/"
        logger.d { "Connecting to $url" }
        try {
            val session = httpClient.webSocketSession(url)
            this.session = session
            scope.launch { receiveFrames(session) }
        } catch (e: Throwable) {
            close()
            throw e
        }
    }

    /** Drains the session's frames into [receiveChannel] until the peer or we close the connection. */
    private suspend fun receiveFrames(session: DefaultClientWebSocketSession) {
        try {
            for (frame in session.incoming) {
                when (frame) {
                    is Frame.Binary -> receiveChannel.writeFully(frame.data)

                    // The router sends binary, because windows-1252 is not valid UTF-8 and a text
                    // frame is UTF-8 by definition. Decode one as that if it ever arrives, rather
                    // than passing its bytes through as if they were the game's charset.
                    is Frame.Text -> receiveChannel.writeFully(frame.readText().encodeWindows1252())

                    // Ping, pong, and close are the session's business, not the game's.
                    else -> Unit
                }
            }
        } catch (e: Exception) {
            logger.d(e) { "WebSocket read failed" }
        } finally {
            // EOF for whoever is reading: readLine()/readAvailable() return null and the client
            // disconnects, exactly as when a TCP peer goes away.
            receiveChannel.close()
        }
    }

    override suspend fun readLine(): String? {
        checkConnected()
        return receiveChannel.readWindows1252Line()
    }

    override suspend fun readAvailable(min: Int): String? {
        checkConnected()
        return receiveChannel.readWindows1252Available(buffer, min)
    }

    override fun ready(): Boolean {
        checkConnected()
        return receiveChannel.availableForRead > 0
    }

    override suspend fun write(text: String) {
        val session = checkNotNull(session) { "Socket not connected" }
        // Writing to a socket that has gone away is an IOException here as it is on TCP, where the
        // byte channel raises one. Callers already treat that as the connection being over; a raw
        // channel exception would escape them instead.
        if (closed) throw IOException("Socket is closed")
        try {
            // Binary, not text: we already hold windows-1252 bytes, and raw high bytes in a text
            // frame are invalid UTF-8, which drops the connection mid-session.
            session.send(Frame.Binary(fin = true, data = text.encodeWindows1252()))
            session.flush()
        } catch (e: ClosedSendChannelException) {
            throw IOException("Socket is closed", e)
        }
    }

    override fun close() {
        logger.d { "Closing connection" }
        closed = true
        // Cancelling the session tears down the connection, which ends the pump. Waiting readers are
        // woken by cancelling the channel rather than closing it: close() is a write-side operation
        // that touches the same buffer the pump writes into, and this runs on whatever thread asked
        // for the close. cancel() only trips an atomic, so the pump keeps sole ownership of the
        // write side; with no cause it reads as a clean end of stream, so a waiting read returns
        // null and the client disconnects the same way it does when the peer hangs up.
        session?.cancel()
        receiveChannel.cancel(null)
        scope.cancel()
    }

    private fun checkConnected() = checkNotNull(session) { "Socket not connected" }
}
