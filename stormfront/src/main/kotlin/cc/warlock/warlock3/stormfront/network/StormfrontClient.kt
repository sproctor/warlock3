package cc.warlock.warlock3.stormfront.network

import cc.warlock.warlock3.core.*
import cc.warlock.warlock3.stormfront.protocol.BaseElementListener
import cc.warlock.warlock3.stormfront.protocol.DataListener
import cc.warlock.warlock3.stormfront.protocol.StartElement
import cc.warlock.warlock3.stormfront.protocol.StormfrontProtocolHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException

class StormfrontClient(host: String, port: Int) : WarlockClient {
    private val socket = Socket(host, port)
    private val eventChannel = MutableSharedFlow<ClientEvent>()
    override val eventFlow: SharedFlow<ClientEvent> = eventChannel.asSharedFlow()
    private var parseText = true
    private val scope = CoroutineScope(Dispatchers.Default)

    private inner class ClientDataListener : DataListener {
        var buffer = StyledString(emptyList())
        override fun characters(text: StyledString) {
            buffer = buffer.append(text)
        }

        override fun eol() {
            val line = buffer
            buffer = StyledString(emptyList())
            scope.launch {
                eventChannel.emit(ClientEvent.ClientDataReceivedEvent(line))
            }
        }
    }

    fun connect(key: String) {
        scope.launch(Dispatchers.IO) {
            sendCommand(key)
            sendCommand("/FE:WARLOCK /XML")

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
                                    eventChannel.emit(
                                        ClientEvent.ClientDataReceivedEvent(
                                            StyledString(
                                                line,
                                                WarlockStyle.monospaced
                                            )
                                        )
                                    )
                                }
                            } else {
                                while (reader.ready()) {
                                    buffer.append(reader.read().toChar())
                                }
                                print(buffer.toString())
                                eventChannel.emit(
                                    ClientEvent.ClientDataReceivedEvent(
                                        StyledString(
                                            buffer.toString(),
                                            WarlockStyle.monospaced
                                        )
                                    )
                                )
                            }
                        }
                    }
                } catch (e: SocketException) {
                    // who knows! let's retry, we can check the result of read/readLine to be sure
                    println("Socket exception: " + e.message)
                } catch (e: SocketTimeoutException) {
                    // Timeout, let's retry here too!
                    println("Socket timeout: " + e.message)
                }
            }
        }
    }

    private suspend fun connectionClosed() {
        // TODO Make this error message a little more sensible
        eventChannel.emit(
            ClientEvent.ClientDataReceivedEvent(
                StyledString(
                    "Connection closed by server.",
                    WarlockStyle.monospaced
                )
            )
        )
        disconnect()
    }

    fun sendCommand(line: String) {
        println("<c>$line");
        send("<c>$line\n")
    }

    override fun disconnect() {
        socket.close()
        scope.launch {
            eventChannel.emit(ClientEvent.ClientDisconnectedEvent())
        }
    }

    override fun send(toSend: String) {
        if (!socket.isOutputShutdown) {
            socket.getOutputStream().write(toSend.toByteArray(Charsets.US_ASCII))
        }
        scope.launch {
            eventChannel.emit(ClientEvent.ClientDataSentEvent(toSend))
        }
    }
}