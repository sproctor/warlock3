package warlockfe.warlock3.wrayth.network

import co.touchlab.kermit.Logger
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.isClosed
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.availableForRead
import io.ktor.utils.io.charsets.TooLongLineException
import io.ktor.utils.io.discard
import io.ktor.utils.io.exhausted
import io.ktor.utils.io.peek
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.readByte
import io.ktor.utils.io.writeByteArray
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.io.Buffer
import kotlinx.io.UnsafeIoApi
import kotlinx.io.readByteArray
import kotlinx.io.unsafe.UnsafeBufferOperations
import kotlinx.io.unsafe.withData
import warlockfe.warlock3.core.client.WarlockSocket
import warlockfe.warlock3.core.util.decodeWindows1252
import warlockfe.warlock3.core.util.encodeWindows1252
import warlockfe.warlock3.wrayth.util.openDefaultTlsSocket
import kotlin.math.min

class NetworkSocket(
    private val dispatcher: CoroutineDispatcher,
    // When true, wrap the connection in TLS (verifying the server cert against the system trust
    // store). Used for the MUD Mobile router; the direct play.net game connection stays plaintext.
    private val secure: Boolean = false,
) : WarlockSocket {
    private val logger = Logger.withTag("NetworkSocket")
    private val selector = SelectorManager(dispatcher)
    private var socket: Socket? = null
    private var closeConnection: (() -> Unit)? = null
    private var closed = false
    private lateinit var sendChannel: ByteWriteChannel
    private lateinit var receiveChannel: ByteReadChannel
    private val buffer = ByteArray(4096)

    override val isClosed: Boolean
        get() = closed || socket?.isClosed == true

    override suspend fun connect(
        host: String,
        port: Int,
    ) {
        logger.d { "Connecting to $host:$port (secure=$secure)" }
        try {
            if (secure) {
                val conn =
                    openDefaultTlsSocket(
                        selectorManager = selector,
                        host = host,
                        port = port,
                        coroutineContext = dispatcher,
                    )
                receiveChannel = conn.readChannel
                sendChannel = conn.writeChannel
                closeConnection = conn.close
            } else {
                val tcpSocket = aSocket(selector).tcp().connect(host, port)
                socket = tcpSocket
                sendChannel = tcpSocket.openWriteChannel(autoFlush = true)
                receiveChannel = tcpSocket.openReadChannel()
            }
        } catch (e: Throwable) {
            close()
            throw e
        }
    }

    override suspend fun readLine(): String? {
        val result = StringBuilder()
        val completed = readWindows1252LineTo(result)
        return if (!completed) null else result.toString()
    }

    private suspend fun readWindows1252LineTo(
        out: Appendable,
        max: Int = Int.MAX_VALUE,
    ): Boolean {
        check(::receiveChannel.isInitialized) { "Socket not connected" }
        with(receiveChannel) {
            if (exhausted()) return false
            if (isClosedForRead) return false

            Buffer().use { lineBuffer ->
                while (!isClosedForRead) {
                    while (!exhausted()) {
                        when (val b = readByte()) {
                            CR -> {
                                // Check if LF follows CR after awaiting
                                awaitContent()
                                val nextByte = peek(1) ?: return true
                                if (nextByte[0] == LF) {
                                    discard(1)
                                }
                                out.append(lineBuffer.readWindows1252String())
                                return true
                            }

                            LF -> {
                                out.append(lineBuffer.readWindows1252String())
                                return true
                            }

                            else -> {
                                lineBuffer.writeByte(b)
                            }
                        }
                    }
                    if (lineBuffer.size >= max) {
                        throw TooLongLineException("Line exceeds limit of $max characters")
                    }

                    awaitContent()
                }

                return (lineBuffer.size > 0).also { remaining ->
                    if (remaining) {
                        out.append(lineBuffer.readWindows1252String())
                    }
                }
            }
        }
    }

    override suspend fun readAvailable(min: Int): String {
        check(::receiveChannel.isInitialized) { "Socket not connected" }
        receiveChannel.awaitContent(min)
        val len = receiveChannel.readAvailable(buffer)
        return buffer.decodeWindows1252(0, len)
    }

    override fun ready(): Boolean {
        check(::receiveChannel.isInitialized) { "Socket not connected" }
        return receiveChannel.availableForRead > 0
    }

    override suspend fun write(text: String) {
        check(::sendChannel.isInitialized) { "Socket not connected" }
        sendChannel.writeByteArray(text.encodeWindows1252())
        sendChannel.flush()
    }

    override fun close() {
        logger.d { "Closing connection" }
        closed = true
        closeConnection?.invoke()
        closeConnection = null
        socket?.close()
        selector.close()
    }
}

private const val CR: Byte = '\r'.code.toByte()
private const val LF: Byte = '\n'.code.toByte()

private fun Buffer.readWindows1252String(): String = readWindows1252(size)

@OptIn(UnsafeIoApi::class)
private fun Buffer.readWindows1252(byteCount: Long): String {
    // Invariant: byteCount was request()'ed into this buffer beforehand
    if (byteCount == 0L) return ""

    UnsafeBufferOperations.forEachSegment(this) { ctx, segment ->
        if (segment.size >= byteCount) {
            var result = ""
            ctx.withData(segment) { data, pos, limit ->
                result = data.decodeWindows1252(pos, min(limit, pos + byteCount.toInt()))
                skip(byteCount)
                return result
            }
        }
        // If the string spans multiple segments, delegate to readBytes()
        return readByteArray(byteCount.toInt()).decodeWindows1252()
    }
    error("Unreacheable")
}
