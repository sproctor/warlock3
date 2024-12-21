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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
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
import warlockfe.warlock3.core.client.SendCommandType
import warlockfe.warlock3.core.client.WarlockClient
import warlockfe.warlock3.core.compass.DirectionType
import warlockfe.warlock3.core.prefs.AlterationRepository
import warlockfe.warlock3.core.prefs.CharacterRepository
import warlockfe.warlock3.core.prefs.WindowRepository
import warlockfe.warlock3.core.prefs.defaultMaxTypeAhead
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
import kotlin.math.max

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

    private var completeFileLogger = FileLogger(logPath / "unknown", "complete", true)
    private var simpleFileLogger: FileLogger? = null

    private val scope = CoroutineScope(Dispatchers.IO)

    private var maxTypeAhead: Int = defaultMaxTypeAhead

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

    private val commandQueue = MutableStateFlow<List<String>>(emptyList())
    private val currentTypeAhead = MutableStateFlow(0)

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
        windows["warlockscripts"] = StormfrontWindow(
            name = "warlockscripts",
            title = "Running scripts",
            subtitle = null,
            ifClosed = "",
            styleIfClosed = null,
        )
        windows["scriptoutput"] = StormfrontWindow(
            name = "scriptoutput",
            title = "Script output",
            subtitle = null,
            ifClosed = "main",
            styleIfClosed = "echo",
        )
        windows["debug"] = StormfrontWindow(
            name = "debug",
            title = "Debug",
            subtitle = null,
            ifClosed = "",
            styleIfClosed = null,
        )
        windowRepository.setWindowTitle("warlockscripts", "Running scripts", null)
        windowRepository.setWindowTitle("scriptoutput", "Script output", null)
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

                        else -> {
                            // do nothing
                        }
                    }
                    text += StyledString(" ") + StyledString("stop", WarlockStyle.Link("action" to "/kill ${it.key}"))
                    scriptStream.appendLine(text, false)
                }
            }
        }
        scope.launch {
            commandQueue.collect { commands ->
                commands.firstOrNull()?.let { command ->
                    if (maxTypeAhead > 0) {
                        currentTypeAhead.first { it < maxTypeAhead }
                    }
                    currentTypeAhead.update { it + 1 }
                    commandQueue.update { it.drop(1) }
                    sendCommandDirect(command)
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

    // Output style is for echo style! Not received text
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
        scope.launch {
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
            sendCommandDirect(key)
            sendCommandDirect("/FE:STORMFRONT /XML")

            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), charsetName))
            val protocolHandler = StormfrontProtocolHandler()

            while (!socket.isClosed) {
                try {
                    if (parseText) {
                        // This is the standard Stormfront parser
                        val line: String? = reader.readLine()
                        if (line != null) {
                            completeFileLogger.write(line)
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
                                            val path = logPath / "${event.game}_${event.character}"
                                            completeFileLogger = FileLogger(path, "complete", true)
                                            simpleFileLogger = FileLogger(path, "simple", false)
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
                                        currentTypeAhead.update { max(0, it - 1) }
                                        currentStyle = null
                                        currentStream = mainStream
                                        if (!isPrompting) {
                                            mainStream.appendPartial(
                                                StyledString(event.text, listOfNotNull(outputStyle))
                                            )
                                            isPrompting = true
                                        }
                                        notifyListeners(ClientPromptEvent)
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
                                        sendCommandDirect("")
                                    }

                                    is StormfrontDialogDataEvent -> dialogDataId = event.id
                                    is StormfrontProgressBarEvent -> {
                                        _properties.value = _properties.value +
                                                (event.id to event.value.value.toString()) +
                                                (event.id + "text" to event.text)
                                        notifyListeners(
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
                                        notifyListeners(ClientCompassEvent(directions.toPersistentHashSet()))
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

                                    StormfrontNavEvent -> notifyListeners(ClientNavEvent)

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
                                        debug("Unhandled tag: ${event.tag}")
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
                                if (buffer.contains("<mode")) {
                                    // if the line starts with a mode tag, drop back to the normal parser
                                    parseText = true
                                    protocolHandler.parseLine(line)
                                } else {
                                    completeFileLogger.write(line)
                                    simpleFileLogger?.write(line)
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
        if (stream == mainStream || ifClosedStream(stream) == mainStream) {
            isPrompting = false
            val text = styledText.toString()
            if (text.isNotBlank()) {
                simpleFileLogger?.write(text)
                notifyListeners(ClientTextEvent(text))
            }
        }
    }

    private suspend fun doAppendToStream(styledText: StyledString, stream: TextStream, ignoreWhenBlank: Boolean) {
        if (ignoreWhenBlank && styledText.isBlank())
            return
        stream.appendLine(styledText, ignoreWhenBlank)
        doIfClosed(stream) { targetStream ->
            val style = windows[stream.name]?.styleIfClosed
            targetStream.appendLine(
                text = style?.let { styledText.applyStyle(WarlockStyle(it)) } ?: styledText,
                ignoreWhenBlank = ignoreWhenBlank
            )
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
        val styledText = buffer ?: StyledString("")
        appendToStream(styledText, currentStream, ignoreWhenBlank)
        buffer = null
    }

    private suspend fun doIfClosed(stream: TextStream, action: suspend (TextStream) -> Unit) {
        if (!openWindows.value.contains(stream.name)) {
            ifClosedStream(stream)?.let { closedStream ->
                action(closedStream)
            }
        }
    }

    private fun ifClosedStream(stream: TextStream): TextStream? {
        val window = windows[stream.name] ?: return null
        val ifClosedName = window.ifClosed ?: "main"
        return if (stream.name != ifClosedName && ifClosedName.isNotBlank()) {
            getStream(ifClosedName)
        } else {
            null
        }
    }

    override suspend fun sendCommand(line: String): SendCommandType {
            printCommand(line)
            simpleFileLogger?.write(">$line\n")
            completeFileLogger.write("command: $line\n")
        return if (line.startsWith(scriptCommandPrefix)) {
            val scriptCommand = line.drop(1)
            scriptManager.startScript(this, scriptCommand)
            SendCommandType.SCRIPT
        } else if (line.startsWith(clientCommandPrefix)) {
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
            SendCommandType.ACTION
        } else {
            commandQueue.update { it + line }
            SendCommandType.COMMAND
        }
    }

    override suspend fun sendCommandDirect(command: String) {
        withContext(Dispatchers.IO) {
            val toSend = "<c>$command\n"
            try {
                socket.getOutputStream().write(toSend.toByteArray(charset))
            } catch (e: SocketException) {
                print(StyledString("Could not send command: ${e.message}", WarlockStyle.Error))
            }
        }
    }

    override suspend fun startScript(scriptCommand: String) {
        scriptManager.startScript(this, scriptCommand)
        SendCommandType.SCRIPT
    }

    override suspend fun disconnect() {
        runCatching {
            socket.close()
        }
        mainStream.appendLine(StyledString("Connection closed by server."))
        _connected.value = false
    }

    private suspend fun rawPrint(text: String) {
        isPrompting = false
        val lines = text.split(newLinePattern)
        lines.dropLast(1).forEach { fullLine ->
            mainStream.appendPartialAndEol(StyledString(fullLine, WarlockStyle.Mono))
        }
        mainStream.appendPartial(StyledString(lines.last(), WarlockStyle.Mono))
    }

    override suspend fun print(message: StyledString) {
        val style = outputStyle
        mainStream.appendLine(if (style != null) message.applyStyle(style) else message)
        notifyListeners(ClientTextEvent(message.toString()))
    }

    private suspend fun printCommand(command: String) {
        val styles = listOfNotNull(outputStyle, WarlockStyle.Command)
        mainStream.appendPartialAndEol(StyledString(command, styles))
        notifyListeners(ClientTextEvent(command))
    }

    override suspend fun debug(message: String) {
        doAppendToStream(StyledString(message), getStream("debug"), false)
    }

    override suspend fun scriptDebug(message: String) {
        doAppendToStream(StyledString(message), getStream("scriptoutput"), false)
    }

    private fun getStream(name: String): TextStream {
        return streamRegistry.getOrCreateStream(name)
    }

    override fun setMaxTypeAhead(value: Int) {
        require(value >= 0)
        maxTypeAhead = value
    }

    private fun notifyListeners(event: ClientEvent) {
        scope.launch {
            _eventFlow.emit(event)
        }
    }

    override fun close() {
        scope.cancel()
    }
}
