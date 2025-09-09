package warlockfe.warlock3.wrayth.network

import warlockfe.warlock3.core.client.WarlockSocket
import java.net.Socket
import java.nio.charset.Charset

private const val charsetName = "windows-1252"
private val charset = Charset.forName(charsetName)

class AndroidSocket(private val socket: Socket) : WarlockSocket {

    private val reader = socket.getInputStream().bufferedReader(charset)

    override val isClosed: Boolean
        get() = socket.isClosed

    override suspend fun readLine(): String? = reader.readLine()

    override suspend fun read(): Int = reader.read()

    override fun ready(): Boolean = reader.ready()

    override suspend fun write(text: String) {
        val outputStream = socket.outputStream
        outputStream?.write(text.toByteArray(charset))
        outputStream?.flush()
    }

    override fun close() {
        socket.close()
    }
}