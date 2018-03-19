package cc.warlock.warlock3.stormfront.network

import cc.warlock.warlock3.core.ClientListener
import cc.warlock.warlock3.core.WarlockClient
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Socket
import kotlin.concurrent.thread

class StormfrontClient(val host: String, val port: Int, val key: String) : WarlockClient {
    override var socket = Socket(host, port)
    override val listeners = ArrayList<ClientListener>()
    private var shutdown: Boolean = false

    init {
        send(key)
        send("/FE:STORMFRONT /VERSION:1.0.1.22 /XML")

        thread(start = true) {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            while (!shutdown && socket.isConnected) {
                val line: String? = reader.readLine()
                if (line != null)
                    notifyListeners(WarlockClient.ClientDataReceivedEvent(line))
            }
        }
    }
}