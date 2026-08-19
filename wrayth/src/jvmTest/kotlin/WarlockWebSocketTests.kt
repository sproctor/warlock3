import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.io.IOException
import warlockfe.warlock3.wrayth.network.WarlockWebSocket
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.websocket.WebSockets as ServerWebSockets

// The MUD Mobile transport, against a real WebSocket server. What matters is that the game's byte
// stream survives the trip in both directions: frames are not message boundaries, and the bytes are
// windows-1252 rather than the UTF-8 a text frame would imply.
class WarlockWebSocketTests {
    /**
     * Serves a WebSocket that pushes [push], one frame each, then either waits for the client
     * ([keepOpen]) or hangs up. [firstReceived] completes with the first frame the client sends.
     */
    private fun withServer(
        push: List<ByteArray> = emptyList(),
        keepOpen: Boolean = true,
        pushAfterMillis: Long = 0,
        test: suspend (socket: WarlockWebSocket, firstReceived: CompletableDeferred<Frame>) -> Unit,
    ) = runBlocking {
        val firstReceived = CompletableDeferred<Frame>()
        val server =
            embeddedServer(ServerCIO, port = 0) {
                install(ServerWebSockets)
                routing {
                    webSocket("/") {
                        if (pushAfterMillis > 0) delay(pushAfterMillis)
                        push.forEach { send(Frame.Binary(fin = true, data = it)) }
                        if (keepOpen) {
                            for (frame in incoming) firstReceived.complete(frame)
                        }
                    }
                }
            }.start(wait = false)
        val client =
            HttpClient(CIO) {
                install(WebSockets)
                // Mirrors the app's client (see AppContainer), with the bound made small enough to
                // fail this test in seconds if it ever started applying to the game stream.
                install(HttpTimeout) { requestTimeoutMillis = REQUEST_TIMEOUT_MILLIS }
            }
        val socket = WarlockWebSocket(client, Dispatchers.IO, secure = false)
        try {
            val port =
                server.engine
                    .resolvedConnectors()
                    .first()
                    .port
            socket.connect("127.0.0.1", port)
            test(socket, firstReceived)
        } finally {
            socket.close()
            client.close()
            server.stop(gracePeriodMillis = 0, timeoutMillis = 1000)
        }
    }

    @Test
    fun whatIsWrittenLeavesAsWindows1252BytesInABinaryFrame() =
        withServer { socket, firstReceived ->
            socket.write("say —\n")

            val frame = withTimeout(AWAIT_TIMEOUT_MILLIS) { firstReceived.await() }
            assertTrue(frame is Frame.Binary, "the game stream is not UTF-8, so it must not be a text frame")
            // 0x97 is the em dash in windows-1252; UTF-8 would have made it three bytes.
            assertContentEquals(
                "say ".encodeToByteArray() + byteArrayOf(0x97.toByte(), '\n'.code.toByte()),
                frame.data,
            )
        }

    @Test
    fun whatArrivesIsReadBackAsLinesNoMatterHowItWasFramed() {
        val push =
            listOf(
                // One line split across two frames, the second of which also carries a whole line.
                "<pushBold/>You see a ".encodeToByteArray(),
                byteArrayOf(0x97.toByte()) + "wolf<popBold/>\r\nand a rock\r\n".encodeToByteArray(),
            )
        withServer(push = push) { socket, _ ->
            assertEquals("<pushBold/>You see a —wolf<popBold/>", socket.readLine())
            assertEquals("and a rock", socket.readLine())
        }
    }

    @Test
    fun theApiRequestTimeoutDoesNotReachTheGameStream() {
        // The router holds a connection for up to ~105s while the cloud machine boots, and a bridged
        // session is then legitimately idle for long stretches. Ktor exempts WebSocket requests from
        // the request timeout in both the plugin and the CIO engine; this is that promise, tested.
        withServer(
            push = listOf("late\r\n".encodeToByteArray()),
            pushAfterMillis = REQUEST_TIMEOUT_MILLIS * 4,
        ) { socket, _ ->
            assertEquals("late", socket.readLine())
        }
    }

    @Test
    fun theServerHangingUpEndsTheStreamRatherThanHanging() {
        withServer(push = listOf("bye\r\n".encodeToByteArray()), keepOpen = false) { socket, _ ->
            assertEquals("bye", socket.readLine())
            assertNull(socket.readLine())
        }
    }

    @Test
    fun theServerHangingUpDoesNotReportTheSocketClosed() {
        // A client reads until a read returns null and announces the disconnect there. It also loops
        // on `while (!socket.isClosed)`, so a socket that called itself closed as soon as the stream
        // ran dry would end that loop first and the disconnect would never be announced - no message,
        // no reconnect banner, no teardown. Closed means we closed it, as it does for a TCP socket.
        withServer(push = listOf("bye\r\n".encodeToByteArray()), keepOpen = false) { socket, _ ->
            assertEquals("bye", socket.readLine())
            assertNull(socket.readLine())
            assertFalse(socket.isClosed, "the peer hanging up is not this socket being closed")

            socket.close()
            assertTrue(socket.isClosed)
        }
    }

    @Test
    fun writingToAClosedSocketRaisesIoExceptionAsItDoesOnTcp() {
        // WraythClient's write path catches IOException and reports the failed command; anything else
        // escapes it and takes the caller down with it - which is what stopped a window from closing.
        withServer { socket, _ ->
            socket.close()

            assertFailsWith<IOException> { socket.write("say hello\n") }
        }
    }

    private companion object {
        const val REQUEST_TIMEOUT_MILLIS = 250L
        const val AWAIT_TIMEOUT_MILLIS = 10_000L
    }
}
