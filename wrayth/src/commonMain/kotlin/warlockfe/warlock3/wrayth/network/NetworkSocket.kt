package warlockfe.warlock3.wrayth.network

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.io.Buffer
import kotlinx.io.UnsafeIoApi
import kotlinx.io.readByteArray
import kotlinx.io.readString
import kotlinx.io.unsafe.UnsafeBufferOperations
import kotlinx.io.unsafe.withData
import warlockfe.warlock3.core.client.WarlockSocket
import warlockfe.warlock3.core.util.decodeWindows1252
import warlockfe.warlock3.core.util.encodeWindows1252
import kotlin.math.min

class NetworkSocket(dispatcher: CoroutineDispatcher) : WarlockSocket {

    private val logger = KotlinLogging.logger {}
    private val selector = SelectorManager(dispatcher)
    private var socket: Socket? = null
    private lateinit var sendChannel: ByteWriteChannel
    private lateinit var receiveChannel: ByteReadChannel
    private val buffer = ByteArray(4096)

    override val isClosed: Boolean
        get() = socket?.isClosed == true

    override suspend fun connect(host: String, port: Int) {
        logger.trace { "Connecting to $host:$port" }
        socket = aSocket(selector).tcp().connect(host, port)
        sendChannel = socket!!.openWriteChannel(autoFlush = true)
        receiveChannel = socket!!.openReadChannel()
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
        check(socket != null)
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
                                out.append(lineBuffer.readString())
                                return true
                            }

                            LF -> {
                                out.append(lineBuffer.readString())
                                return true
                            }

                            else -> lineBuffer.writeByte(b)
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
        check(socket != null)
        receiveChannel.awaitContent(min)
        val len = receiveChannel.readAvailable(buffer)
        return buffer.decodeWindows1252(0, len)
    }

    override fun ready(): Boolean {
        check(socket != null)
        return receiveChannel.availableForRead > 0
    }

    override suspend fun write(text: String) {
        check(socket != null)
        sendChannel.writeByteArray(text.encodeWindows1252())
        sendChannel.flush()
    }

    override fun close() {
        socket?.close()
    }
}

private const val CR: Byte = '\r'.code.toByte()
private const val LF: Byte = '\n'.code.toByte()

private fun Buffer.readWindows1252String(): String {
    return readWindows1252(size)
}

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
