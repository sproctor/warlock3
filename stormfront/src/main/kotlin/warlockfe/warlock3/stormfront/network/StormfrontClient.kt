package warlockfe.warlock3.stormfront.network

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.minus
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.plus
import kotlinx.collections.immutable.toPersistentHashSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.Path
import warlockfe.warlock3.core.client.ClientCompassEvent
import warlockfe.warlock3.core.client.ClientEvent
import warlockfe.warlock3.core.client.ClientNavEvent
import warlockfe.warlock3.core.client.ClientProgressBarEvent
import warlockfe.warlock3.core.client.ClientPromptEvent
import warlockfe.warlock3.core.client.ClientTextEvent
import warlockfe.warlock3.core.client.GameCharacter
import warlockfe.warlock3.core.client.ProgressBarData
import warlockfe.warlock3.core.client.WarlockClient
import warlockfe.warlock3.core.compass.DirectionType
import warlockfe.warlock3.core.prefs.AlterationRepository
import warlockfe.warlock3.core.prefs.CharacterRepository
import warlockfe.warlock3.core.prefs.WindowRepository
import warlockfe.warlock3.core.script.ScriptManager
import warlockfe.warlock3.core.script.ScriptStatus
import warlockfe.warlock3.core.text.StyledString
import warlockfe.warlock3.core.text.StyledStringVariable
import warlockfe.warlock3.core.text.WarlockStyle
import warlockfe.warlock3.core.text.isBlank
import warlockfe.warlock3.core.window.StreamRegistry
import warlockfe.warlock3.core.window.TextStream
import warlockfe.warlock3.scripting.wsl.splitFirstWord
import warlockfe.warlock3.stormfront.protocol.StormfrontActionEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontAppEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontCastTimeEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontClearStreamEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontCompassEndEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontComponentDefinitionEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontComponentEndEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontComponentStartEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontDataReceivedEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontDialogDataEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontDirectionEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontEolEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontHandledEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontModeEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontNavEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontOutputEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontParseErrorEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontProgressBarEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontPromptEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontPropertyEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontProtocolHandler
import warlockfe.warlock3.stormfront.protocol.StormfrontRoundTimeEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontSettingsInfoEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontStreamEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontStreamWindowEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontStyleEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontTimeEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontUnhandledTagEvent
import warlockfe.warlock3.stormfront.stream.StormfrontWindow
import warlockfe.warlock3.stormfront.util.AlterationResult
import warlockfe.warlock3.stormfront.util.CompiledAlteration
import warlockfe.warlock3.stormfront.util.FileLogger
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
    private val key: String,
    private val logPath: Path,
    private val windowRepository: WindowRepository,
    private val characterRepository: CharacterRepository,
    private val scriptManager: ScriptManager,
    private val alterationRepository: AlterationRepository,
    private val streamRegistry: StreamRegistry,
) : WarlockClient {

    private val logger = KotlinLogging.logger {}

    private val newLinePattern = Regex("\r?\n")

    private var fileLogger: FileLogger

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

    private val mainStream = getStream("main")
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

    init {
        fileLogger = FileLogger(logPath / "unknown")
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
            scriptManager.scriptInfo.collect { scripts ->
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
    override val connected = _connected.asStateFlow()

    private var delta = 0L
    override val time: Long
        get() = System.currentTimeMillis() + delta

    override fun connect() {
        scope.launch(Dispatchers.IO) {
            try {
                logger.debug { "Opening connection to $host:$port" }
                socket = Socket(host, port)
            } catch (e: UnknownHostException) {
                logger.debug { "Unknown host" }
                _connected.value = false
                return@launch
            } catch (e: IOException) {
                logger.error(e) { "Error connecting to $host:$port" }
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
                            fileLogger.write(line)
                            debug(line)
                            val events = protocolHandler.parseLine(line)
                            events.forEach { event ->
                                when (event) {
                                    StormfrontHandledEvent -> Unit
                                    is StormfrontModeEvent ->
                                        if (event.id.equals("cmgr", true)) {
                                            parseText = false
                                        }
                                    is StormfrontStreamEvent -> {
                                        flushBuffer(true)
                                        currentStream = if (event.id != null) {
                                            getStream(event.id)
                                        } else {
                                            mainStream
                                        }
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
                                            fileLogger = FileLogger(logPath / "${event.game}_${event.character}")
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
                                            isPrompting = true
                                        }
                                    }
                                    is StormfrontTimeEvent -> {
                                        val newTime = event.time.toLong() * 1000L
                                        val currentTime = time
                                        if (newTime > currentTime + 1000L) {
                                            // We're more than 1s slow
                                            delta = newTime - System.currentTimeMillis() - 1000L
                                        } else if (newTime < currentTime - 1000L) {
                                            // We're more than 1s fast
                                            delta = newTime - System.currentTimeMillis() + 1000L
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
                                            val newValue = buffer ?: StyledString("")
                                            streamRegistry.getStreams().forEach { stream ->
                                                stream.updateComponent(componentId!!, newValue)
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
                                fileLogger.write(line)
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
                    logger.debug { "Socket exception: " + e.message }
                    disconnect()
                } catch (e: SocketTimeoutException) {
                    logger.debug { "Socket timeout: " + e.message }
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
        if (ignoreWhenBlank && styledText.isBlank())
            return
        stream.appendLine(styledText, ignoreWhenBlank)
        if (stream == mainStream)
            isPrompting = false
        doIfClosed(stream.name) { targetStream ->
            val currentWindow = windows[stream.name]
            targetStream.appendLine(
                text = currentWindow?.styleIfClosed?.let { styledText.applyStyle(WarlockStyle(it)) } ?: styledText,
                ignoreWhenBlank = ignoreWhenBlank
            )
            if (stream == mainStream)
                isPrompting = false
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
        buffer?.let { _eventFlow.emit(ClientTextEvent(it.toString())) }
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
            scriptManager.startScript(this, scriptCommand)
            printCommand(line)
        } else if (line.startsWith(clientCommandPrefix)) {
            print(StyledString(line, WarlockStyle.Command))
            val clientCommand = line.drop(1)
            val (command, args) = clientCommand.splitFirstWord()
            when (command) {
                "kill" -> {
                    args?.split(' ')?.forEach { name ->
                        scriptManager.findScriptInstance(name)?.stop()
                    }
                }
                "pause" -> {
                    args?.split(' ')?.forEach { name ->
                        scriptManager.findScriptInstance(name)?.suspend()
                    }
                }
                "resume" -> {
                    args?.split(' ')?.forEach { name ->
                        scriptManager.findScriptInstance(name)?.resume()
                    }
                }
                "list" -> {
                    val scripts = scriptManager.runningScripts
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
                printCommand(line)
            }
        }
    }

    private suspend fun doSendCommand(line: String) {
        send("<c>$line\n")
    }

    override suspend fun disconnect() {
        runCatching {
            socket.close()
        }
        mainStream.appendLine(StyledString("Connection closed by server."))
        _connected.value = false
    }

    private suspend fun send(toSend: String) {
        withContext(Dispatchers.IO) {
            fileLogger.write(toSend)
            if (!socket.isOutputShutdown) {
                socket.getOutputStream().write(toSend.toByteArray(charset))
            }
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

    private suspend fun printCommand(command: String) {
        scope.launch {
            _eventFlow.emit(ClientTextEvent(command))
        }
        val styles = listOfNotNull(outputStyle, WarlockStyle.Command)
        mainStream.appendPartial(StyledString(command, styles))
        mainStream.appendEol()
    }

    override suspend fun debug(message: String) {
        doAppendToStream(StyledString(message), getStream("debug"), false)
    }

    @Synchronized
    fun getStream(name: String): TextStream {
        return streamRegistry.getOrCreateStream(name)
    }
}