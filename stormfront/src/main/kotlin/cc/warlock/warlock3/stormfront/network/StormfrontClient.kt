package cc.warlock.warlock3.stormfront.network

import cc.warlock.warlock3.core.client.*
import cc.warlock.warlock3.core.compass.DirectionType
import cc.warlock.warlock3.core.prefs.CharacterRepository
import cc.warlock.warlock3.core.prefs.WindowRepository
import cc.warlock.warlock3.core.text.StyledString
import cc.warlock.warlock3.core.text.WarlockStyle
import cc.warlock.warlock3.stormfront.TextStream
import cc.warlock.warlock3.stormfront.protocol.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap

class StormfrontClient(
    private val host: String,
    private val port: Int,
    private val windowRepository: WindowRepository,
    private val characterRepository: CharacterRepository,
) : WarlockClient {
    private val scope = CoroutineScope(Dispatchers.Default)

    override var maxTypeAhead: Int = 1
        private set

    private lateinit var socket: Socket

    private val _eventFlow = MutableSharedFlow<ClientEvent>()
    override val eventFlow: SharedFlow<ClientEvent> = _eventFlow.asSharedFlow()

    private val _characterId = MutableStateFlow<String?>(null)
    override val characterId: StateFlow<String?> = _characterId.asStateFlow()

    private val _properties = MutableStateFlow<Map<String, String>>(emptyMap())
    override val properties: StateFlow<Map<String, String>> = _properties.asStateFlow()

    private val _components = MutableStateFlow<Map<String, StyledString>>(emptyMap())
    override val components: StateFlow<Map<String, StyledString>> = _components.asStateFlow()

    private val mainStream = TextStream("main")
    private val streams = ConcurrentHashMap(mapOf("main" to mainStream))

    @OptIn(ExperimentalCoroutinesApi::class)
    private val openWindows = characterId.flatMapLatest { characterId ->
        if (characterId != null) {
            windowRepository.observeOpenWindows(characterId)
        } else {
            flow { }
        }
    }
        .stateIn(scope, started = SharingStarted.Eagerly, initialValue = emptyList())

    private var parseText = true

    private var currentStream: TextStream = mainStream
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
            try {
                println("Opening connection to $host:$port")
                socket = Socket(host, port)
            } catch (e: UnknownHostException) {
                println("Unknown host")
                _connected.value = false
                return@launch
            } catch (e: IOException) {
                e.printStackTrace()
                _connected.value = false
                return@launch
            }
            sendCommand(key, echo = false)
            sendCommand("/FE:STORMFRONT /XML", echo = false)

            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), "windows-1252"))
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
                                    StormfrontHandledEvent -> Unit
                                    is StormfrontModeEvent ->
                                        if (event.id.equals("cmgr", true)) {
                                            parseText = false
                                        }
                                    is StormfrontStreamEvent ->
                                        currentStream = if (event.id != null) {
                                            getStream(event.id)
                                        } else {
                                            mainStream
                                        }
                                    is StormfrontDataReceivedEvent -> {
                                        val styles = listOfNotNull(currentStyle)
                                        currentStream.append(event.text, styles = styles)
                                        doIfClosed(currentStream.name) { targetStream ->
                                            val currentWindow = windowRepository.getWindow(currentStream.name)
                                            targetStream.append(
                                                event.text,
                                                styles = styles + listOfNotNull(currentWindow?.styleIfClosed?.let {
                                                    WarlockStyle(it)
                                                })
                                            )
                                        }
                                    }
                                    is StormfrontEolEvent -> {
                                        currentStream.appendEol(event.ignoreWhenBlank)?.let { text ->
                                            _eventFlow.emit(ClientTextEvent(text))
                                        }
                                        doIfClosed(currentStream.name) { targetStream ->
                                            targetStream.appendEol(event.ignoreWhenBlank)
                                        }
                                    }
                                    is StormfrontAppEvent -> {
                                        val game = event.game
                                        val character = event.character
                                        _characterId.value = if (game != null && character != null) {
                                            val characterId = "${event.game}:${event.character}".lowercase()
                                            if (characterRepository.getCharacter(characterId) == null) {
                                                characterRepository.saveCharacter(
                                                    GameCharacter(
                                                        accountId = null,
                                                        id = characterId,
                                                        gameCode = game,
                                                        name = character
                                                    )
                                                )
                                            }
                                            characterId
                                        } else {
                                            null
                                        }
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
                                        currentStream.appendPrompt(event.text)
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
                                        _properties.value = _properties.value +
                                                (event.id to event.value.value.toString()) +
                                                (event.id + "text" to event.text)
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
                                        currentStream.appendVariable(name = event.id, styles = styles)
                                    }
                                    is StormfrontComponentStartEvent -> {
                                        componentId = event.id
                                        componentText = null
                                    }
                                    is StormfrontComponentTextEvent -> {
                                        val string = StyledString(
                                            text = event.text,
                                            styles = listOfNotNull(currentStyle)
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
                                        windowRepository.addWindow(event.window)
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
                                    mainStream.append(text = line, styles = listOf(WarlockStyle.Mono))
                                }
                            } else {
                                while (reader.ready()) {
                                    buffer.append(reader.read().toChar())
                                }
                                print(buffer.toString())
                                mainStream.append(
                                    text = buffer.toString(),
                                    styles = listOf(WarlockStyle.Mono)
                                )
                            }
                        }
                    }
                } catch (e: SocketException) {
                    println("Socket exception: " + e.message)
                    disconnect()
                } catch (e: SocketTimeoutException) {
                    println("Socket timeout: " + e.message)
                    disconnect()
                }
            }
        }
    }

    private suspend fun doIfClosed(streamName: String, action: suspend (TextStream) -> Unit) {
        val currentWindow = windowRepository.getWindow(streamName)
        val ifClosed = currentWindow?.ifClosed ?: "main"
        if (ifClosed != streamName && ifClosed.isNotBlank() && !openWindows.value.contains(streamName)) {
            action(getStream(ifClosed))
        }
    }

    override suspend fun sendCommand(line: String, echo: Boolean) {
        send("<c>$line\n")
        if (echo) {
            mainStream.appendCommand(line)
            scope.launch {
                _eventFlow.emit(ClientTextEvent(line))
            }
        }
    }

    override suspend fun disconnect() {
        runCatching {
            socket.close()
        }
        mainStream.appendMessage(StyledString("Connection closed by server."))
        _connected.value = false
    }

    private fun send(toSend: String) {
        println(toSend)
        if (!socket.isOutputShutdown) {
            socket.getOutputStream().write(toSend.toByteArray(Charsets.US_ASCII))
        }
    }

    override suspend fun print(message: StyledString) {
        scope.launch {
            _eventFlow.emit(ClientTextEvent(message.toString()))
        }
        val style = outputStyle
        mainStream.appendMessage(if (style != null) message.applyStyle(style) else message)
    }

    override suspend fun debug(message: String) {
        mainStream.appendMessage(StyledString(message, WarlockStyle.Echo))
    }

    @Synchronized
    fun getStream(name: String): TextStream {
        return streams.getOrPut(name) { TextStream(name) }
    }
}