package cc.warlock.warlock3.stormfront.network

import cc.warlock.warlock3.core.client.*
import cc.warlock.warlock3.core.compass.DirectionType
import cc.warlock.warlock3.core.prefs.*
import cc.warlock.warlock3.core.script.ScriptStatus
import cc.warlock.warlock3.core.script.WarlockScriptEngineRegistry
import cc.warlock.warlock3.core.script.wsl.splitFirstWord
import cc.warlock.warlock3.core.text.StyledString
import cc.warlock.warlock3.core.text.StyledStringVariable
import cc.warlock.warlock3.core.text.WarlockStyle
import cc.warlock.warlock3.stormfront.protocol.*
import cc.warlock.warlock3.stormfront.stream.StormfrontWindow
import cc.warlock.warlock3.stormfront.stream.TextStream
import cc.warlock.warlock3.stormfront.util.AlterationResult
import cc.warlock.warlock3.stormfront.util.CompiledAlteration
import cc.warlock.warlock3.stormfront.util.FileLogger
import kotlinx.collections.immutable.*
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
import java.nio.charset.Charset
import java.util.concurrent.ConcurrentHashMap

const val scriptCommandPrefix = '.'
const val clientCommandPrefix = '/'
private const val charsetName = "windows-1252"
private val charset = Charset.forName(charsetName)

class StormfrontClient(
    private val host: String,
    private val port: Int,
    private val windowRepository: WindowRepository,
    private val characterRepository: CharacterRepository,
    private val characterSettingsRepository: CharacterSettingsRepository,
    private val scriptEngineRegistry: WarlockScriptEngineRegistry,
    private val alterationRepository: AlterationRepository,
) : WarlockClient {

    private val newLinePattern = Regex("\r?\n")

    private var logger: FileLogger

    private val scope = CoroutineScope(Dispatchers.Default)

    override var maxTypeAhead: Int = 1
        private set

    private lateinit var socket: Socket

    private val _eventFlow = MutableSharedFlow<ClientEvent>()
    override val eventFlow: SharedFlow<ClientEvent> = _eventFlow.asSharedFlow()

    private val _characterId = MutableStateFlow<String?>(null)
    override val characterId: StateFlow<String?> = _characterId.asStateFlow()

    private val _properties = MutableStateFlow<PersistentMap<String, String>>(persistentMapOf())
    override val properties: StateFlow<ImmutableMap<String, String>> = _properties.asStateFlow()

    private val _components = MutableStateFlow<PersistentMap<String, StyledString>>(persistentMapOf())
    override val components: StateFlow<ImmutableMap<String, StyledString>> = _components.asStateFlow()

    private val mainStream = TextStream("main", this)
    private val streams = ConcurrentHashMap(mapOf("main" to mainStream))
    private val windows = ConcurrentHashMap<String, StormfrontWindow>()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val alterations: StateFlow<List<CompiledAlteration>> = characterId.flatMapLatest { characterId ->
        if (characterId != null)
            alterationRepository.observeForCharacter(characterId).map { list ->
                list.map { CompiledAlteration(it) }
            }
        else
            flow { }
    }
        .stateIn(scope = scope, started = SharingStarted.Eagerly, initialValue = emptyList())

    // Line state variables
    private var isPrompting = false
    private var buffer: StyledString? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    val scrollback = characterId.flatMapLatest { characterId ->
        if (characterId != null) {
            characterSettingsRepository.observe(characterId, scrollbackKey)
                .map { it?.toIntOrNull() ?: defaultMaxScrollLines }
        } else {
            flow { emit(defaultMaxScrollLines) }
        }
    }
        .stateIn(scope = scope, started = SharingStarted.Eagerly, initialValue = defaultMaxScrollLines)

    init {
        val path = System.getProperty("WARLOCK_LOG_DIR")
        logger = FileLogger(path, "unknown")
        windows["warlockscripts"] = StormfrontWindow(
            name = "warlockscripts",
            title = "Running scripts",
            subtitle = null,
            ifClosed = "",
            styleIfClosed = null,
        )
        windows["debug"] = StormfrontWindow(
            name = "debug",
            title = "Debug",
            subtitle = null,
            ifClosed = "",
            styleIfClosed = null,
        )
        windowRepository.setWindowTitle("warlockscripts", "Running scripts", null)
        windowRepository.setWindowTitle("debug", "Debug", null)
        scope.launch {
            val scriptStream = getStream("warlockscripts")
            scriptEngineRegistry.scriptInfo.collect { scripts ->
                scriptStream.clear()
                scripts.forEach {
                    val info = it.value
                    var text = StyledString("${info.name}: ${info.status} ")
                    when (info.status) {
                        ScriptStatus.Running -> text += StyledString(
                            "pause",
                            WarlockStyle.Link("action" to "/pause ${it.key}")
                        )
                        ScriptStatus.Suspended -> text += StyledString(
                            "resume",
                            WarlockStyle.Link("action" to "/resume ${it.key}")
                        )
                        else -> { /* do nothing */
                        }
                    }
                    text += StyledString(" ") + StyledString("stop", WarlockStyle.Link("action" to "/kill ${it.key}"))
                    scriptStream.appendLine(text, false)
                }
            }
        }
    }

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
    private val directions: HashSet<DirectionType> = hashSetOf()
    private var componentId: String? = null

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
            doSendCommand(key)
            doSendCommand("/FE:STORMFRONT /XML")

            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), charsetName))
            val protocolHandler = StormfrontProtocolHandler()

            while (!socket.isClosed) {
                try {
                    if (parseText) {
                        // This is the standard Stormfront parser
                        val line: String? = reader.readLine()
                        if (line != null) {
                            logger.write(line)
                            debug(line)
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
                                    is StormfrontClearStreamEvent ->
                                        getStream(event.id).clear()
                                    is StormfrontDataReceivedEvent -> {
                                        bufferText(StyledString(event.text))
                                    }
                                    is StormfrontEolEvent -> {
                                        // We're working under the assumption that an end tag is always on the same line as the start tag
                                        flushBuffer(event.ignoreWhenBlank)
                                    }
                                    is StormfrontAppEvent -> {
                                        val game = event.game
                                        val character = event.character
                                        _characterId.value = if (game != null && character != null) {
                                            val characterId = "${event.game}:${event.character}".lowercase()
                                            windowRepository.setCharacterId(characterId)
                                            val path = System.getProperty("WARLOCK_LOG_DIR")
                                            logger = FileLogger(path, "${event.game}_${event.character}")
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
                                        val newProperties = _properties.value
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
                                        currentStream = mainStream
                                        if (!isPrompting) {
                                            mainStream.appendPartial(
                                                StyledString(event.text, listOfNotNull(outputStyle))
                                            )
                                        }
                                        isPrompting = true
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
                                        doSendCommand("")
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
                                        _eventFlow.emit(ClientCompassEvent(directions.toPersistentHashSet()))
                                        directions.clear()
                                    }
                                    is StormfrontDirectionEvent -> {
                                        directions += event.direction
                                    }
                                    is StormfrontPropertyEvent -> {
                                        if (event.value != null)
                                            _properties.value += event.key to event.value
                                        else
                                            _properties.value -= event.key
                                    }
                                    is StormfrontComponentDefinitionEvent -> {
                                        // Should not happen on main stream, so don't clear prompt
                                        val styles = currentStyle?.let { persistentListOf(it) } ?: persistentListOf()
                                        bufferText(
                                            text = StyledString(
                                                persistentListOf(
                                                    StyledStringVariable(name = event.id, styles = styles)
                                                )
                                            ),
                                        )
                                    }
                                    is StormfrontComponentStartEvent -> {
                                        flushBuffer(true)
                                        componentId = event.id
                                    }
                                    StormfrontComponentEndEvent -> {
                                        if (componentId != null) {
                                            // Either replace the component in the map with the new value
                                            //  or remove the component from the map (if we got an empty one)
                                            if (buffer?.substrings.isNullOrEmpty()) {
                                                _components.value -= componentId!!
                                            } else {
                                                _components.value += (componentId!! to buffer!!)
                                            }
                                            buffer = null
                                            componentId = null
                                        } else {
                                            // mismatched component tags?
                                        }
                                    }
                                    StormfrontNavEvent -> _eventFlow.emit(ClientNavEvent)
                                    is StormfrontStreamWindowEvent -> {
                                        val window = event.window
                                        windows[window.name] = window
                                        windowRepository.setWindowTitle(
                                            name = window.name,
                                            title = window.title,
                                            subtitle = window.subtitle
                                        )
                                    }
                                    is StormfrontActionEvent -> {
                                        bufferText(
                                            StyledString(
                                                event.text,
                                                WarlockStyle.Link("action" to event.command)
                                            )
                                        )
                                    }
                                    is StormfrontUnhandledTagEvent -> {
                                        // mainStream.append(StyledString("Unhandled tag: ${event.tag}", WarlockStyle.Error))
                                    }
                                    is StormfrontParseErrorEvent -> {
                                        mainStream.appendLine(
                                            StyledString(
                                                "parse error: ${event.text}",
                                                WarlockStyle.Error
                                            )
                                        )
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
                                logger.write(line)
                                if (buffer.contains("<mode")) {
                                    // if the line starts with a mode tag, drop back to the normal parser
                                    parseText = true
                                    protocolHandler.parseLine(line)
                                } else {
                                    rawPrint(line)
                                }
                            } else {
                                while (reader.ready()) {
                                    buffer.append(reader.read().toChar())
                                }
                                print(buffer.toString())
                                rawPrint(buffer.toString())
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

    private fun bufferText(text: StyledString) {
        var styledText = text
        currentStyle?.let { styledText = styledText.applyStyle(it) }
        outputStyle?.let { styledText = styledText.applyStyle(it) }
        buffer = buffer?.plus(styledText) ?: styledText
    }

    private suspend fun appendToStream(styledText: StyledString, stream: TextStream, ignoreWhenBlank: Boolean) {
        val alteration = findAlteration(styledText.toString(), stream.name)
        if (alteration != null) {
            if (alteration.alteration.keepOriginal) {
                doAppendToStream(styledText, stream, ignoreWhenBlank)
            }
            val alteredText = alteration.text?.let { StyledString(it) } ?: styledText
            val destinationStream = alteration.alteration.destinationStream?.let { getStream(it) } ?: stream
            doAppendToStream(alteredText, destinationStream, ignoreWhenBlank)
        } else {
            doAppendToStream(styledText, stream, ignoreWhenBlank)
        }
    }

    private suspend fun doAppendToStream(styledText: StyledString, stream: TextStream, ignoreWhenBlank: Boolean) {
        stream.appendLine(styledText, ignoreWhenBlank)
        if (stream == mainStream) isPrompting = false
        doIfClosed(stream.name) { targetStream ->
            val currentWindow = windows[stream.name]
            targetStream.appendLine(
                text = currentWindow?.styleIfClosed?.let { styledText.applyStyle(WarlockStyle(it)) } ?: styledText,
                ignoreWhenBlank = ignoreWhenBlank
            )
            if (stream == mainStream) isPrompting = false
        }
    }

    private fun findAlteration(text: String, streamName: String): AlterationResult? {
        alterations.value.forEach { alteration ->
            val result = alteration.match(text, streamName)
            if (result != null) return result
        }
        return null
    }

    // TODO: separate buffer into its own class
    private suspend fun flushBuffer(ignoreWhenBlank: Boolean) {
        assert(componentId == null)
        appendToStream(buffer ?: StyledString(""), currentStream, ignoreWhenBlank)
        buffer = null
    }

    private suspend fun doIfClosed(streamName: String, action: suspend (TextStream) -> Unit) {
        val currentWindow = windows[streamName]
        if (currentWindow != null) {
            val ifClosed = currentWindow.ifClosed ?: "main"
            if (ifClosed != streamName && ifClosed.isNotBlank() && !openWindows.value.contains(streamName)) {
                action(getStream(ifClosed))
            }
        }
    }

    override suspend fun sendCommand(line: String, echo: Boolean) {
        if (line.startsWith(scriptCommandPrefix)) {
            val scriptCommand = line.drop(1)
            scriptEngineRegistry.startScript(this, scriptCommand)
            print(StyledString(line, WarlockStyle.Command))
        } else if (line.startsWith(clientCommandPrefix)) {
            print(StyledString(line, WarlockStyle.Command))
            val clientCommand = line.drop(1)
            val (command, args) = clientCommand.splitFirstWord()
            when (command) {
                "kill" -> {
                    args?.split(' ')?.forEach { name ->
                        scriptEngineRegistry.findScriptInstance(name)?.stop()
                    }
                }
                "pause" -> {
                    args?.split(' ')?.forEach { name ->
                        scriptEngineRegistry.findScriptInstance(name)?.suspend()
                    }
                }
                "resume" -> {
                    args?.split(' ')?.forEach { name ->
                        scriptEngineRegistry.findScriptInstance(name)?.resume()
                    }
                }
                "list" -> {
                    val scripts = scriptEngineRegistry.runningScripts
                    if (scripts.isEmpty()) {
                        print(StyledString("No scripts are running", WarlockStyle.Echo))
                    } else {
                        print(StyledString("Running scripts:", WarlockStyle.Echo))
                        scripts.forEach {
                            print(StyledString("${it.name} - ${it.id}", WarlockStyle.Echo))
                        }
                    }
                }
                else -> {
                    print(StyledString("Invalid command.", WarlockStyle.Error))
                }
            }
        } else {
            doSendCommand(line)
            if (echo) {
                mainStream.appendPartial(StyledString(line, listOfNotNull(WarlockStyle.Command, outputStyle)))
                mainStream.appendEol()
                scope.launch {
                    _eventFlow.emit(ClientTextEvent(line))
                }
            }
        }
    }

    private fun doSendCommand(line: String) {
        send("<c>$line\n")
    }

    override suspend fun disconnect() {
        runCatching {
            socket.close()
        }
        mainStream.appendLine(StyledString("Connection closed by server."))
        _connected.value = false
    }

    private fun send(toSend: String) {
        logger.write(toSend)
        if (!socket.isOutputShutdown) {
            socket.getOutputStream().write(toSend.toByteArray(charset))
        }
    }

    private suspend fun rawPrint(text: String) {
        isPrompting = false
        val lines = text.split(newLinePattern)
        lines.dropLast(1).forEach { fullLine ->
            mainStream.appendPartial(StyledString(fullLine, WarlockStyle.Mono))
            mainStream.appendEol()
        }
        mainStream.appendPartial(StyledString(lines.last(), WarlockStyle.Mono))
    }

    override suspend fun print(message: StyledString) {
        scope.launch {
            _eventFlow.emit(ClientTextEvent(message.toString()))
        }
        val style = outputStyle
        mainStream.appendLine(if (style != null) message.applyStyle(style) else message)
    }

    override suspend fun debug(message: String) {
        doAppendToStream(StyledString(message), getStream("debug"), false)
    }

    @Synchronized
    fun getStream(name: String): TextStream {
        return streams.getOrPut(name) { TextStream(name, this) }
    }
}