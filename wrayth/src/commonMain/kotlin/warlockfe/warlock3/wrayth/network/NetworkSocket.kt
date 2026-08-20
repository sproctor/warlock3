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
import io.ktor.utils.io.writeByteArray
import kotlinx.coroutines.CoroutineDispatcher
import warlockfe.warlock3.core.client.WarlockSocket
import warlockfe.warlock3.core.util.encodeWindows1252

class NetworkSocket(
    dispatcher: CoroutineDispatcher,
) : WarlockSocket {
    private val logger = Logger.withTag("NetworkSocket")
    private val selector = SelectorManager(dispatcher)
    private var socket: Socket? = null
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
        logger.d { "Connecting to $host:$port" }
        try {
            val tcpSocket = aSocket(selector).tcp().connect(host, port)
            socket = tcpSocket
            sendChannel = tcpSocket.openWriteChannel(autoFlush = true)
            receiveChannel = tcpSocket.openReadChannel()
        } catch (e: Throwable) {
            close()
            throw e
        }
    }

    override suspend fun readLine(): String? {
        check(::receiveChannel.isInitialized) { "Socket not connected" }
        return receiveChannel.readWindows1252Line()
    }

    override suspend fun readAvailable(min: Int): String? {
        check(::receiveChannel.isInitialized) { "Socket not connected" }
        return receiveChannel.readWindows1252Available(buffer, min)
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
        socket?.close()
        selector.close()
    }
}
