package warlockfe.warlock3.telnet.network

import co.touchlab.kermit.Logger
import io.ktor.network.selector.SelectorManager
import io.ktor.utils.io.availableForRead
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withTimeoutOrNull
import warlockfe.warlock3.wrayth.util.TLSSocketConnection
import warlockfe.warlock3.wrayth.util.openPlainSocket
import warlockfe.warlock3.wrayth.util.openTLSSocket
import kotlin.time.Duration

/**
 * The TCP (or TLS) connection to a telnet MUD, as bytes. Telnet's commands and the text encoding
 * are both byte-level affairs, so unlike the Wrayth socket nothing is decoded here; the client
 * runs the bytes through the telnet decoder and then the text decoder itself.
 */
class TelnetSocket(
    ioDispatcher: CoroutineDispatcher,
) {
    private val logger = Logger.withTag("TelnetSocket")
    private val selector = SelectorManager(ioDispatcher)

    // Owns the IO the platform socket may run in the background (the iOS implementation drives
    // Network.framework from coroutines parented here), so closing the socket cancels it.
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private var connection: TLSSocketConnection? = null
    private var closed = false

    val isClosed: Boolean
        get() = closed

    suspend fun connect(
        host: String,
        port: Int,
        tls: Boolean,
    ) {
        logger.d { "Connecting to $host:$port (tls=$tls)" }
        try {
            connection =
                if (tls) {
                    openTLSSocket(
                        selectorManager = selector,
                        host = host,
                        port = port,
                        certificate = null,
                        coroutineContext = scope.coroutineContext,
                    )
                } else {
                    openPlainSocket(
                        selectorManager = selector,
                        host = host,
                        port = port,
                        coroutineContext = scope.coroutineContext,
                    )
                }
        } catch (e: Throwable) {
            close()
            throw e
        }
    }

    /**
     * Reads whatever has arrived into [buffer], waiting for at least one byte. The number of bytes
     * read, or -1 once the peer has gone and nothing is left: the end of a connection is where every
     * connection is headed, not an error.
     */
    suspend fun read(buffer: ByteArray): Int = channel().readChannel.readAvailable(buffer)

    /**
     * Waits up to [timeout] for more bytes. True when some are there (or the peer has gone, which
     * the next [read] reports); false when the wait ran out, meaning the server has stopped
     * sending for the moment.
     */
    suspend fun awaitContent(timeout: Duration): Boolean {
        val readChannel = channel().readChannel
        if (readChannel.availableForRead > 0 || readChannel.isClosedForRead) return true
        return withTimeoutOrNull(timeout) {
            readChannel.awaitContent()
            true
        } ?: (readChannel.availableForRead > 0 || readChannel.isClosedForRead)
    }

    suspend fun write(bytes: ByteArray) {
        val writeChannel = channel().writeChannel
        writeChannel.writeFully(bytes)
        writeChannel.flush()
    }

    fun close() {
        if (closed) return
        logger.d { "Closing connection" }
        closed = true
        connection?.close?.invoke()
        scope.cancel()
        selector.close()
    }

    private fun channel(): TLSSocketConnection = checkNotNull(connection) { "Socket not connected" }
}
