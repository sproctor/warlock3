package cc.warlock.warlock3.stormfront.network

import cc.warlock.warlock3.core.client.*
import cc.warlock.warlock3.core.compass.DirectionType
import cc.warlock.warlock3.core.text.StyleRegistry
import cc.warlock.warlock3.core.text.StyledString
import cc.warlock.warlock3.core.text.WarlockStyle
import cc.warlock.warlock3.core.text.flattenStyles
import cc.warlock.warlock3.core.window.WindowRegistry
import cc.warlock.warlock3.stormfront.TextStream
import cc.warlock.warlock3.stormfront.protocol.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap

class StormfrontClient(
    host: String,
    port: Int,
    override val maxTypeAhead: Int,
    private val windowRegistry: WindowRegistry,
    private val styleRegistry: StyleRegistry,
) : WarlockClient {
    private val socket = Socket(host, port)
    private val _eventFlow = MutableSharedFlow<ClientEvent>()
    override val eventFlow: SharedFlow<ClientEvent> = _eventFlow.asSharedFlow()

    private val _characterId = MutableStateFlow<String?>(null)
    override val characterId: StateFlow<String?> = _characterId.asStateFlow()

    private val _properties = MutableStateFlow<Map<String, String>>(emptyMap())
    override val properties: StateFlow<Map<String, String>> = _properties.asStateFlow()

    private val _components = MutableStateFlow<Map<String, StyledString>>(emptyMap())
    override val components: StateFlow<Map<String, StyledString>> = _components.asStateFlow()

    private val mainStream = TextStream("main", styleRegistry)
    private val streams = ConcurrentHashMap(mapOf("main" to mainStream))

    private var parseText = true
    private val scope = CoroutineScope(Dispatchers.Default)

    private var currentStream: TextStream? = mainStream
    private var currentStyle: WarlockStyle? = null
    // Output style is for echo style! Not receieved text
    private var outputStyle: WarlockStyle? = null
    private var dialogDataId: String? = null
    private var directions: List<DirectionType> = emptyList()
    private var componentId: String? = null
    private var componentText: StyledString? = null

    private val _connected = MutableStateFlow(true)
    val connected = _connected.asStateFlow()

    private var delta = 0L
    override val time: Long
        get() = System.currentTimeMillis() + delta

    fun connect(key: String) {
        scope.launch(Dispatchers.IO) {
            sendCommand(key, echo = false)
            sendCommand("/FE:STORMFRONT /XML", echo = false)

            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val protocolHandler = StormfrontProtocolHandler(styleRegistry)

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
                                    is StormfrontStreamEvent ->
                                        currentStream = if (event.id != null) {
                                            val ifClosed = windowRegistry.getWindow(event.id)?.ifClosed
                                            if (ifClosed?.isNotBlank() == true && !windowRegistry.isOpen(event.id)) {
                                                getStream(ifClosed)
                                            } else {
                                                getStream(event.id)
                                            }
                                        } else {
                                            mainStream
                                        }
                                    is StormfrontDataReceivedEvent -> {
                                        val styles = listOfNotNull(currentStyle)
                                        currentStream?.append(event.text, styles = styles)
                                    }
                                    is StormfrontEolEvent -> {
                                        currentStream?.appendEol(event.ignoreWhenBlank)?.let { text ->
                                            _eventFlow.emit(ClientTextEvent(text))
                                        }
                                    }
                                    is StormfrontAppEvent -> {
                                        _characterId.value = "${event.game}:${event.character}"
                                        val newProperties = properties.value
                                            .plus("character" to (event.character ?: ""))
                                            .plus("game" to (event.game ?: ""))
                                        _properties.value = newProperties
                                    }
                                    is StormfrontOutputEvent ->
                                        outputStyle = event.style
                                    is StormfrontStyleEvent ->
                                        currentStyle = event.style
                                    is StormfrontPromptEvent -> {
                                        _eventFlow.emit(ClientPromptEvent)
                                        currentStyle = null
                                        currentStream?.appendPrompt(event.text)
                                    }
                                    is StormfrontTimeEvent -> {
                                        val newTime = event.time.toLong() * 1000L
                                        val currentTime = time
                                        if (newTime > currentTime + 1000L) {
                                            // We're more than 1s slow
                                            delta = newTime - System.currentTimeMillis() - 1000L
                                            println("adjusted delta ahead to $delta")
                                        } else if (newTime < currentTime - 1000L) {
                                            // We're more than 1s fast
                                            delta = newTime - System.currentTimeMillis() + 1000L
                                            println("adjusted delta back to $delta")
                                        }
                                    }
                                    is StormfrontRoundTimeEvent ->
                                        _properties.value += "roundtime" to event.time
                                    is StormfrontCastTimeEvent ->
                                        _properties.value += "casttime" to event.time
                                    is StormfrontSettingsInfoEvent -> {
                                        // We don't actually handle server settings

                                        // Not 100% where this belongs. connections hang until and empty command is sent
                                        // This must be in response to either mode, playerId, or settingsInfo, so
                                        // we put it here until someone discovers something else
                                        sendCommand("")
                                    }
                                    is StormfrontDialogDataEvent -> dialogDataId = event.id
                                    is StormfrontProgressBarEvent -> {
                                        _eventFlow.emit(
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
                                        _eventFlow.emit(ClientCompassEvent(directions))
                                        directions = emptyList()
                                    }
                                    is StormfrontDirectionEvent -> {
                                        directions = directions + event.direction
                                    }
                                    is StormfrontPropertyEvent -> {
                                        if (event.value != null)
                                            _properties.value = _properties.value.plus(event.key to event.value)
                                        else
                                            _properties.value = _properties.value.minus(event.key)
                                    }
                                    is StormfrontComponentDefinitionEvent -> {
                                        val styles = listOfNotNull(currentStyle)
                                        currentStream?.appendVariable(name = event.id, styles = styles)
                                    }
                                    is StormfrontComponentStartEvent -> {
                                        componentId = event.id
                                        componentText = null
                                    }
                                    is StormfrontComponentTextEvent -> {
                                        val string = StyledString(
                                            text = event.text,
                                            style = flattenStyles(listOfNotNull(currentStyle))
                                        )
                                        componentText = componentText?.plus(string) ?: string
                                    }
                                    StormfrontComponentEndEvent -> {
                                        if (componentId != null) {
                                            if (componentText?.substrings.isNullOrEmpty()) {
                                                _components.value -= componentId!!
                                            } else {
                                                _components.value += (componentId!! to componentText!!)
                                            }
                                        }
                                        componentText = null
                                    }
                                    StormfrontNavEvent -> _eventFlow.emit(ClientNavEvent)
                                    is StormfrontStreamWindowEvent -> {
                                        windowRegistry.addWindow(event.window)
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
                                    mainStream.append(text = line, styles = listOf(WarlockStyle(monospace = true)))
                                }
                            } else {
                                while (reader.ready()) {
                                    buffer.append(reader.read().toChar())
                                }
                                print(buffer.toString())
                                mainStream.append(
                                    text = buffer.toString(),
                                    styles = listOf(WarlockStyle(monospace = true))
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

    override fun sendCommand(line: String, echo: Boolean) {
        if (echo) {
            mainStream.appendCommand(line)
        }
        send("<c>$line\n")
    }

    override fun disconnect() {
        socket.close()
        mainStream.appendMessage(StyledString("Connection closed by server."))
        _connected.value = false
    }

    private fun send(toSend: String) {
        println(toSend)
        if (!socket.isOutputShutdown) {
            socket.getOutputStream().write(toSend.toByteArray(Charsets.US_ASCII))
        }
    }

    override fun print(message: StyledString) {
        scope.launch {
            _eventFlow.emit(ClientTextEvent(message.toPlainString()))
        }
        val style = outputStyle
        mainStream.appendMessage(if (style != null) message.applyStyle(style) else message)
    }

    @Synchronized
    fun getStream(name: String): TextStream {
        return streams.getOrPut(name) { TextStream(name, styleRegistry) }
    }
}