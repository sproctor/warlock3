package cc.warlock.warlock3.stormfront.network

import cc.warlock.warlock3.core.ClientListener
import cc.warlock.warlock3.core.WarlockClient
import cc.warlock.warlock3.stormfront.protocol.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Socket
import java.util.*
import kotlin.concurrent.thread

class StormfrontClient(val host: String, val port: Int, val key: String) : WarlockClient {
    override var socket = Socket(host, port)
    override val listeners = LinkedList<ClientListener>()
    private var shutdown: Boolean = false
    private var parseText = true

    // FIXME: this is kind of hacky
    private var lineHasTags = false
    private var lineHasText = false

    inner class ClientDataListener : DataListener {
        val buffer = StringBuffer()

        override fun characters(data: String) {
            lineHasText = true
            buffer.append(data)
        }

        override fun done() {
            if (lineHasText || !lineHasTags) {
                buffer.append("\n")
            }
            notifyListeners(WarlockClient.ClientDataReceivedEvent(buffer.toString()))
            buffer.setLength(0)
            lineHasTags = false
            lineHasText = false
        }
    }

    inner class ClientElementListner : ElementListener {
        override fun startElement(element: StartElement) {
            lineHasTags = true
            if (element.name == "mode" && element.attributes.get("id") == "CMGR") {
                parseText = false
            }
        }

        override fun endElement(element: EndElement) {
            lineHasTags = true
        }
    }

    fun connect() {
        send(key)
        send("/FE:STORMFRONT /VERSION:1.0.1.22 /XML")

        thread(start = true) {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val protocolHandler = StormfrontProtocolHandler()
            protocolHandler.addDataListener(ClientDataListener())
            protocolHandler.addElementListener(ClientElementListner())

            while (!shutdown && socket.isConnected) {
                if (parseText) {
                    val line: String? = reader.readLine()
                    if (line != null) {
                        println(line)
                        protocolHandler.parseLine(line)
                    } else {
                        println("got null line from server")
                        shutdown = true
                    }
                } else {
                    val buffer = StringBuilder()
                    val char = reader.read().toChar()
                    buffer.append(char)
                    if (char == '<') {
                        buffer.append(reader.readLine())
                        val line = buffer.toString()
                        println(line)
                        if (buffer.contains("<mode")) {
                            parseText = true
                            protocolHandler.parseLine(line)
                        } else {
                            WarlockClient.ClientDataReceivedEvent(line)
                        }
                    } else {
                        while (reader.ready()) {
                            buffer.append(reader.read().toChar())
                        }
                        print(buffer.toString())
                        notifyListeners(WarlockClient.ClientDataReceivedEvent(buffer.toString()))
                    }
                }
            }
        }
    }
}