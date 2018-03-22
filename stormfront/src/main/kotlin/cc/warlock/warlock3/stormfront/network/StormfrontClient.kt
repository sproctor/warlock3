package cc.warlock.warlock3.stormfront.network

import cc.warlock.warlock3.core.ClientListener
import cc.warlock.warlock3.core.StyledString
import cc.warlock.warlock3.core.WarlockClient
import cc.warlock.warlock3.core.WarlockStyle
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

    private val gameViewListener = object : WarlockClient.ClientViewListener {
        override fun commandEntered(command: String) {
            sendCommand(command)
        }
    }

    inner class ClientDataListener : DataListener {
        var buffer = StyledString()
        override fun characters(text: StyledString) {
            buffer.append(text)
        }
        override fun done() {
            notifyListeners(WarlockClient.ClientDataReceivedEvent(buffer))
            buffer = StyledString()
        }
    }

    fun connect() {
        sendCommand(key)
        sendCommand("/FE:STORMFRONT /VERSION:1.0.1.22 /XML")

        thread(start = true) {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val protocolHandler = StormfrontProtocolHandler()
            protocolHandler.addDataListener(ClientDataListener())
            protocolHandler.addElementListener("mode", object : BaseElementListener() {
                override fun startElement(element: StartElement) {
                    if (element.attributes["id"] == "CMGR") {
                        parseText = false
                    }
                }
            })

            while (!shutdown && socket.isConnected) {
                if (parseText) {
                    // This is the standard Stormfront parser
                    val line: String? = reader.readLine()
                    if (line != null) {
                        println(line)
                        protocolHandler.parseLine(line)
                    } else {
                        println("got null line from server")
                        shutdown = true
                    }
                } else {
                    // This is the strange mode to read books and create characters
                    val buffer = StringBuilder()
                    val char = reader.read().toChar()
                    buffer.append(char)
                    // check for <mode> tag
                    if (char == '<') {
                        buffer.append(reader.readLine())
                        val line = buffer.toString()
                        println(line)
                        if (buffer.contains("<mode")) {
                            // if the line starts with a mode tag, drop back to the normal parser
                            parseText = true
                            protocolHandler.parseLine(line)
                        } else {
                            notifyListeners(WarlockClient.ClientDataReceivedEvent(StyledString(line, WarlockStyle.monospaced)))
                        }
                    } else {
                        while (reader.ready()) {
                            buffer.append(reader.read().toChar())
                        }
                        print(buffer.toString())
                        notifyListeners(WarlockClient.ClientDataReceivedEvent(StyledString(buffer.toString(), WarlockStyle.monospaced)))
                    }
                }
            }
        }
    }

    fun sendCommand(line: String) {
        send("<c>$line\n")
    }

    override fun getClientViewListener(): WarlockClient.ClientViewListener {
        return gameViewListener
    }
}