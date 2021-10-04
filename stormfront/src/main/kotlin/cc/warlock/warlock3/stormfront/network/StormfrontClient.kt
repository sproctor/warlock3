package cc.warlock.warlock3.stormfront.network

import cc.warlock.warlock3.core.*
import cc.warlock.warlock3.core.compass.DirectionType
import cc.warlock.warlock3.stormfront.protocol.*
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

    private var currentStream: String? = null
    private var currentStyle: WarlockStyle? = null
    private var outputStyle: WarlockStyle? = null
    private var dialogDataId: String? = null
    private var directions: List<DirectionType> = emptyList()
    private var componentId: String? = null
    private var componentText: StyledString? = null

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
                                when (event) {
                                    is StormfrontModeEvent ->
                                        if (event.id.equals("cmgr", true)) {
                                            parseText = false
                                        }
                                    is StormfrontStreamEvent -> currentStream = event.id
                                    is StormfrontDataReceivedEvent -> {
                                        val styles = listOfNotNull(currentStyle, outputStyle)
                                        eventChannel.emit(
                                            ClientDataReceivedEvent(
                                                text = event.text,
                                                styles = styles,
                                                stream = currentStream
                                            )
                                        )
                                    }
                                    is StormfrontEolEvent ->
                                        eventChannel.emit(ClientEolEvent(currentStream))
                                    is StormfrontAppEvent -> {
                                        eventChannel.emit(
                                            ClientPropertyChangedEvent(
                                                name = "character",
                                                value = event.character
                                            )
                                        )
                                        eventChannel.emit(ClientPropertyChangedEvent(name = "game", value = event.game))
                                    }
                                    is StormfrontOutputEvent ->
                                        outputStyle = event.style
                                    is StormfrontStyleEvent ->
                                        currentStyle = event.style
                                    is StormfrontPromptEvent -> {
                                        currentStyle = null
                                        outputStyle = null
                                        eventChannel.emit(ClientPromptEvent(event.text))
                                    }
                                    is StormfrontTimeEvent ->
                                        eventChannel.emit(ClientPropertyChangedEvent(name = "time", value = event.time))
                                    is StormfrontRoundTimeEvent ->
                                        eventChannel.emit(
                                            ClientPropertyChangedEvent(
                                                name = "roundtime",
                                                value = event.time
                                            )
                                        )
                                    is StormfrontSettingsInfoEvent -> {
                                        // We don't actually handle server settings

                                        // Not 100% where this belongs. connections hang until and empty command is sent
                                        // This must be in response to either mode, playerId, or settingsInfo, so
                                        // we put it here until someone discovers something else
                                        sendCommand("")
                                    }
                                    is StormfrontDialogDataEvent -> dialogDataId = event.id
                                    is StormfrontProgressBarEvent -> {
                                        eventChannel.emit(
                                            ClientProgressBarEvent(
                                                ProgressBarData(
                                                    id = event.id,
                                                    groupId = dialogDataId ?: "",
                                                    left = event.left,
                                                    width = event.width,
                                                    text = event.text,
                                                    value = event.value,
                                                )
                                            )
                                        )
                                    }
                                    is StormfrontCompassEndEvent -> {
                                        eventChannel.emit(ClientCompassEvent(directions))
                                        directions = emptyList()
                                    }
                                    is StormfrontDirectionEvent -> {
                                        directions = directions + event.direction
                                    }
                                    is StormfrontPropertyEvent -> {
                                        eventChannel.emit(ClientPropertyChangedEvent(event.key, event.value))
                                    }
                                    is StormfrontComponentStartEvent -> {
                                        componentId = event.id
                                        componentText = null
                                    }
                                    is StormfrontComponentTextEvent -> {
                                        val string = StyledString(
                                            text = event.text,
                                            style = flattenStyles(listOfNotNull(currentStyle, outputStyle))
                                        )
                                        componentText = componentText?.append(string) ?: string
                                    }
                                    StormfrontComponentEndEvent -> {
                                        if (componentId != null && componentText != null) {
                                            eventChannel.emit(
                                                ClientComponentUpdateEvent(
                                                    id = componentId!!,
                                                    text = componentText!!
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            // connection was closed by server
                            disconnect()
                        }
                    } else {
                        // This is the strange mode to read books and create characters
                        val buffer = StringBuilder()
                        val c = reader.read()
                        if (c == -1) {
                            // connection was closed by server
                            disconnect()
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

    override fun sendCommand(line: String) {
        scope.launch {
            eventChannel.emit(ClientCommandEvent(line))
        }
        send("<c>$line\n")
    }

    override fun disconnect() {
        socket.close()
        scope.launch {
            eventChannel.emit(ClientDisconnectedEvent)
        }
    }

    override fun send(toSend: String) {
        println(toSend)
        if (!socket.isOutputShutdown) {
            socket.getOutputStream().write(toSend.toByteArray(Charsets.US_ASCII))
        }
    }

    override fun print(message: StyledString) {
        scope.launch {
            eventChannel.emit(ClientOutputEvent(message))
        }
    }
}