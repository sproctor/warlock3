package cc.warlock.warlock3.stormfront.network

import cc.warlock.warlock3.core.ClientListener
import cc.warlock.warlock3.core.StyledString
import cc.warlock.warlock3.core.WarlockClient
import cc.warlock.warlock3.core.WarlockStyle
import cc.warlock.warlock3.stormfront.protocol.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.*
import kotlin.concurrent.thread

class StormfrontClient(val host: String, val port: Int, val key: String) : WarlockClient {
    override var socket = Socket(host, port)
    override val listeners = LinkedList<ClientListener>()
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

            while (!socket.isClosed) {
                try {
                    if (parseText) {
                        // This is the standard Stormfront parser
                        val line: String? = reader.readLine()
                        if (line != null) {
                            println(line)
                            protocolHandler.parseLine(line)
                        } else {
                            // connection was closed by server
                            connectionClosed()
                        }
                    } else {
                        // This is the strange mode to read books and create characters
                        val buffer = StringBuilder()
                        val c = reader.read()
                        if (c == -1) {
                            // connection was closed by server
                            connectionClosed()
                        } else {
                            val char = c.toChar()
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
                } catch (e: SocketException) {
                    // who knows! let's retry, we can check the result of read/readLine to be sure
                    println("Socket exception: " + e.message)
                }  catch (e: SocketTimeoutException) {
                    // Timeout, let's retry here too!
                    println("Socket timeout: " + e.message)
                }
            }
        }
    }

    private fun connectionClosed() {
        // TODO Make this error message a little more sensible
        notifyListeners(WarlockClient.ClientDataReceivedEvent(StyledString("Connection closed by server.",
                WarlockStyle.monospaced)))
        disconnect()
    }

    fun sendCommand(line: String) {
        send("<c>$line\n")
    }

    override fun getClientViewListener(): WarlockClient.ClientViewListener {
        return gameViewListener
    }
}