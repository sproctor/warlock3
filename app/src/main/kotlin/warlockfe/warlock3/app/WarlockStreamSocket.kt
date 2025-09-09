package warlockfe.warlock3.app

import warlockfe.warlock3.core.client.WarlockSocket
import java.io.InputStream

class WarlockStreamSocket(private val inputStream: InputStream) : WarlockSocket {

    private val reader = inputStream.bufferedReader()

    override var isClosed: Boolean = false

    override suspend fun readLine(): String? = reader.readLine()

    override suspend fun read(): Int = reader.read()

    override fun ready(): Boolean = reader.ready()

    override suspend fun write(text: String) {
        // Nothing to do here
    }

    override fun close() {
        inputStream.close()
        isClosed = true
    }
}