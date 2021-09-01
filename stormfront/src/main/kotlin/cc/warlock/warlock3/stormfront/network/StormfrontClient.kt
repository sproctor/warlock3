package cc.warlock.warlock3.stormfront.network

import cc.warlock.warlock3.core.*
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

    fun connect(key: String) {
        scope.launch(Dispatchers.IO) {
            sendCommand(key)
            sendCommand("/FE:STORMFRONT /XML")

            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val protocolHandler = StormfrontProtocolHandler()

            while (!socket.isClosed) {
                try {
                    if (parseText) {
                        // This is the standard Stormfront parser
                        val line: String? = reader.readLine()
                        if (line != null) {
                            println(line)
                            val events = protocolHandler.parseLine(line)
                            events.forEach { event ->
                                if (event is ClientPropertyChangedEvent
                                    && event.name == "mode"
                                    && event.value.equals("cmgr", true)
                                ) {
                                    parseText = false
                                }
                                eventChannel.emit(event)
                            }
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
                                        ClientOutputEvent(
                                            StyledString(
                                                line,
                                                WarlockStyle(monospace = true)
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
                                    ClientOutputEvent(
                                        StyledString(
                                            buffer.toString(),
                                            WarlockStyle(monospace = true)
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
            ClientOutputEvent(
                StyledString(
                    "Connection closed by server.",
                    WarlockStyle(monospace = true)
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
            eventChannel.emit(ClientDisconnectedEvent)
        }
    }

    override fun send(toSend: String) {
        if (!socket.isOutputShutdown) {
            socket.getOutputStream().write(toSend.toByteArray(Charsets.US_ASCII))
        }
        scope.launch {
            eventChannel.emit(ClientDataSentEvent(toSend))
        }
    }
}