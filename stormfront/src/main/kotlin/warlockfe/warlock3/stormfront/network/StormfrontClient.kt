package warlockfe.warlock3.stormfront.network

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.minus
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.plus
import kotlinx.collections.immutable.toPersistentHashSet
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import warlockfe.warlock3.core.client.ClientCompassEvent
import warlockfe.warlock3.core.client.ClientDialogClearEvent
import warlockfe.warlock3.core.client.ClientDialogEvent
import warlockfe.warlock3.core.client.ClientEvent
import warlockfe.warlock3.core.client.ClientNavEvent
import warlockfe.warlock3.core.client.ClientOpenUrlEvent
import warlockfe.warlock3.core.client.ClientPromptEvent
import warlockfe.warlock3.core.client.ClientTextEvent
import warlockfe.warlock3.core.client.DialogObject
import warlockfe.warlock3.core.client.GameCharacter
import warlockfe.warlock3.core.client.SendCommandType
import warlockfe.warlock3.core.client.WarlockAction
import warlockfe.warlock3.core.client.WarlockClient
import warlockfe.warlock3.core.client.WarlockMenuData
import warlockfe.warlock3.core.client.WarlockMenuItem
import warlockfe.warlock3.core.compass.DirectionType
import warlockfe.warlock3.core.prefs.CharacterRepository
import warlockfe.warlock3.core.prefs.LoggingRepository
import warlockfe.warlock3.core.prefs.WindowRepository
import warlockfe.warlock3.core.prefs.defaultMaxTypeAhead
import warlockfe.warlock3.core.text.StyledString
import warlockfe.warlock3.core.text.StyledStringVariable
import warlockfe.warlock3.core.text.WarlockStyle
import warlockfe.warlock3.core.text.isBlank
import warlockfe.warlock3.core.window.StreamRegistry
import warlockfe.warlock3.core.window.TextStream
import warlockfe.warlock3.core.window.WindowType
import warlockfe.warlock3.stormfront.protocol.StormfrontActionEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontAppEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontCastTimeEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontClearStreamEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontCliEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontCompassEndEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontComponentDefinitionEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontComponentEndEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontComponentStartEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontDataReceivedEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontDialogDataEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontDialogObjectEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontDialogWindowEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontDirectionEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontEndCmdList
import warlockfe.warlock3.stormfront.protocol.StormfrontEolEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontHandledEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontMenuEndEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontMenuItemEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontMenuStartEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontModeEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontNavEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontOpenUrlEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontOutputEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontParseErrorEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontPopStyleEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontPromptEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontPropertyEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontProtocolHandler
import warlockfe.warlock3.stormfront.protocol.StormfrontPushCmdEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontPushStyleEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontResourceEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontRoundTimeEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontSettingsInfoEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontStartCmdList
import warlockfe.warlock3.stormfront.protocol.StormfrontStreamEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontStreamWindowEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontStyleEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontTimeEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontUnhandledTagEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontUpdateVerbsEvent
import warlockfe.warlock3.stormfront.util.CmdDefinition
import warlockfe.warlock3.stormfront.util.StormfrontCmd
import warlockfe.warlock3.stormfront.util.StormfrontStreamWindow
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.URI
import java.nio.charset.Charset
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

const val scriptCommandPrefix = '.'
const val clientCommandPrefix = '/'
private const val charsetName = "windows-1252"
private val charset = Charset.forName(charsetName)

class StormfrontClient(
    private val windowRepository: WindowRepository,
    private val characterRepository: CharacterRepository,
    private val streamRegistry: StreamRegistry,
    private val fileLogging: LoggingRepository
) : WarlockClient {

    private val writeContext = Dispatchers.IO.limitedParallelism(1)

    private val logger = KotlinLogging.logger {}

    private val newLinePattern = Regex("\r?\n")

    private val scope = CoroutineScope(Dispatchers.IO)

    private var maxTypeAhead: Int = defaultMaxTypeAhead

    private var socket: Socket? = null

    private var proxyProcess: Process? = null

    private var gameCode: String? = null

    private val _eventFlow = MutableSharedFlow<ClientEvent>()
    override val eventFlow: SharedFlow<ClientEvent> = _eventFlow.asSharedFlow()

    private val _characterId = MutableStateFlow<String?>(null)
    override val characterId: StateFlow<String?> = _characterId.asStateFlow()

    private val _properties = MutableStateFlow<PersistentMap<String, String>>(persistentMapOf())
    override val properties: StateFlow<ImmutableMap<String, String>> = _properties.asStateFlow()

    private val _components = MutableStateFlow<PersistentMap<String, StyledString>>(persistentMapOf())
    override val components: StateFlow<ImmutableMap<String, StyledString>> = _components.asStateFlow()

    private val logBuffer = mutableListOf<suspend () -> Unit>()
    private var logName: String? = null

    private val mainStream = getStream("main")
    private val windows = ConcurrentHashMap<String, StormfrontStreamWindow>()

    private val commandQueue = Channel<String>(Channel.UNLIMITED)
    private val currentTypeAhead = MutableStateFlow(0)

    private var menuCount = 0

    // Line state variables
    private var isPrompting = false
    private var buffer: StyledString? = null

    private val cliCache = mutableListOf<CmdDefinition>()

    private var cliCoords = persistentMapOf<String, CmdDefinition>()

    private val _menuData = MutableStateFlow(WarlockMenuData(0, emptyList()))
    override val menuData: StateFlow<WarlockMenuData> = _menuData.asStateFlow()
    private var currentCmd: StormfrontCmd? = null

    private var currentMenuId: Int = 0

    private val cachedMenuItems = mutableListOf<WarlockMenuItem>()

    init {
        listOf(
            StormfrontStreamWindow(
                id = "warlockscripts",
                title = "Running scripts",
                subtitle = null,
                ifClosed = "",
                styleIfClosed = null,
                timestamp = false,
            ),
            StormfrontStreamWindow(
                id = "scriptoutput",
                title = "Script output",
                subtitle = null,
                ifClosed = "main",
                styleIfClosed = "echo",
                timestamp = false,
            ),
            StormfrontStreamWindow(
                id = "debug",
                title = "Debug",
                subtitle = null,
                ifClosed = "",
                styleIfClosed = null,
                timestamp = false,
            ),
        ).forEach { addWindow(it) }
        scope.launch {
            commandQueue.consumeEach { command ->
                if (maxTypeAhead > 0) {
                    currentTypeAhead.first { it < maxTypeAhead }
                }
                currentTypeAhead.update { it + 1 }
                sendCommandDirect(command)
            }
        }
    }

    private val openWindows = windowRepository.openWindows
        .stateIn(scope, started = SharingStarted.Eagerly, initialValue = emptyList())

    private var parseText = true

    private var currentStream: TextStream = mainStream

    private var currentStyle: WarlockStyle? = null
    private val styleStack = Stack<WarlockStyle>()

    // Output style gets applied to echoed text as well
    private var outputStyle: WarlockStyle? = null

    private var dialogDataId: String? = null
    private val directions: HashSet<DirectionType> = hashSetOf()
    private var componentId: String? = null

    private val _disconnected = MutableStateFlow(false)
    override val disconnected = _disconnected.asStateFlow()

    private var delta = 0L
    override val time: Long
        get() = System.currentTimeMillis() + delta

    override suspend fun connect(inputStream: InputStream, socket: Socket?, key: String) {

        this.socket = socket

        scope.launch {
            sendCommandDirect(key)
            sendCommandDirect("/FE:WRAYTH /VERSION:1.0.1.28 /P:WIN_UNKNOWN /XML")

            val reader = BufferedReader(InputStreamReader(inputStream, charsetName))
            val protocolHandler = StormfrontProtocolHandler()

            while (socket?.isClosed != true) {
                try {
                    if (parseText) {
                        // This is the standard Stormfront parser
                        val line: String? = reader.readLine()
                        if (line == null) {
                            // Connection closed by server
                            disconnected()
                            break
                        }
                        logComplete { line }
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
                                        logName = "${event.game}_${event.character}"
                                        logBuffer.forEach {
                                            it()
                                        }
                                        logBuffer.clear()
                                        if (characterRepository.getCharacter(characterId) == null) {
                                            characterRepository.saveCharacter(
                                                GameCharacter(
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

                                is StormfrontPushStyleEvent ->
                                    styleStack.push(event.style)

                                StormfrontPopStyleEvent ->
                                    if (styleStack.isNotEmpty())
                                        styleStack.pop()

                                is StormfrontPromptEvent -> {
                                    currentTypeAhead.update { max(0, it - 1) }
                                    styleStack.clear()
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
                                    val newTime = event.time * 1000L
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
                                    gameCode = event.instance

                                    // We don't actually handle server settings

                                    // Not 100% where this belongs. connections hang until and empty command is sent
                                    // This must be in response to either mode, playerId, or settingsInfo, so
                                    // we put it here until someone discovers something else
                                    sendCommandDirect("")
                                    sendCommandDirect("_STATE CHATMODE OFF")
                                }

                                is StormfrontDialogDataEvent -> {
                                    dialogDataId = event.id
                                    if (event.clear && event.id != null) {
                                        notifyListeners(ClientDialogClearEvent(event.id))
                                    }
                                }

                                is StormfrontDialogObjectEvent -> {
                                    val data = event.data
                                    if (data is DialogObject.ProgressBar) {
                                        _properties.value = _properties.value +
                                                (data.id to data.value.value.toString()) +
                                                ((data.id + "text") to (data.text ?: ""))
                                    }
                                    dialogDataId?.let {
                                        notifyListeners(ClientDialogEvent(it, data))
                                    }
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
                                    // TODO: Should currentStyle be used here? is it per stream?
                                    val styles = styleStack.toPersistentList()
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
                                    if (windows.get(event.window.id) == null && window.id != "main") {
                                        sendCommandDirect("_swclose s${event.window.id}")
                                    }
                                    addWindow(window)
                                }

                                is StormfrontDialogWindowEvent -> {
                                    val window = event.window
                                    if (window.resident) {
                                        windowRepository.setWindowTitle(
                                            name = window.id,
                                            title = window.title,
                                            subtitle = null,
                                            windowType = WindowType.DIALOG,
                                            showTimestamps = false,
                                        )
                                    }
                                }

                                is StormfrontActionEvent -> {
                                    bufferText(
                                        StyledString(
                                            text = event.text,
                                            style = WarlockStyle.Link(WarlockAction.SendCommand(event.command)),
                                        )
                                    )
                                }

                                is StormfrontOpenUrlEvent -> {
                                    try {
                                        val url = URI("https://www.play.net/")
                                            .resolve(event.url)
                                        notifyListeners(ClientOpenUrlEvent(url))
                                    } catch (_: Exception) {
                                        // Silently ignore exceptions
                                    }
                                }

                                is StormfrontUpdateVerbsEvent -> {
                                    sendCommandDirect("_menu update 1")
                                }

                                is StormfrontStartCmdList -> {
                                    // ignore for now
                                }

                                is StormfrontEndCmdList -> {
                                    cliCoords = cliCoords.mutate { map ->
                                        cliCache.forEach { cli ->
                                            map[cli.coord] = cli
                                        }
                                    }
                                    cliCache.clear()
                                }

                                is StormfrontCliEvent -> {
                                    cliCache.add(event.cmd)
                                }

                                is StormfrontPushCmdEvent -> {
                                    val cmd = event.cmd
                                    if (cmd.coord != null) {
                                        val cmdDef = cliCoords[cmd.coord]
                                        if (cmdDef != null) {
                                            val command = cmdDef.command.replace("@", cmd.noun ?: "")
                                            styleStack.push(
                                                WarlockStyle.Link(WarlockAction.SendCommand(command))
                                            )
                                        } else {
                                            debug("Could not find cli for coord: ${cmd.coord}")
                                            styleStack.push(WarlockStyle(""))
                                        }
                                    } else {
                                        if (cmd.exist != null) {
                                            styleStack.push(
                                                WarlockStyle.Link(
                                                    WarlockAction.OpenMenu {
                                                        val menuId = menuCount++
                                                        _menuData.value = WarlockMenuData(menuId, emptyList())
                                                        currentCmd = cmd
                                                        scope.launch {
                                                            sendCommandDirect("_menu #${cmd.exist} $menuId")
                                                        }
                                                        menuId
                                                    }
                                                )
                                            )
                                        } else {
                                            styleStack.push(WarlockStyle(""))
                                        }
                                    }
                                }

                                is StormfrontMenuStartEvent -> {
                                    event.id?.let {
                                        currentMenuId = it
                                    }
                                }

                                is StormfrontMenuItemEvent -> {
                                    cliCoords[event.coord]?.let { command ->
                                        cachedMenuItems.add(
                                            WarlockMenuItem(
                                                label = command.menu
                                                    .replace("@", currentCmd?.noun ?: "")
                                                    .replace("%", event.noun ?: ""),
                                                category = command.category,
                                                action = {
                                                    val noun = currentCmd?.exist?.let { "#$it" } ?: currentCmd?.noun
                                                    if (noun != null) {
                                                        sendCommand(
                                                            command.command
                                                                .replace("#", noun)
                                                                .replace("%", event.noun ?: "")
                                                        )
                                                    } else {
                                                        print(
                                                            StyledString(
                                                                "Command noun is null",
                                                                WarlockStyle.Error
                                                            )
                                                        )
                                                    }
                                                }
                                            )
                                        )
                                    }
                                }

                                is StormfrontMenuEndEvent -> {
                                    _menuData.update { menu ->
                                        if (menu.id != currentMenuId) {
                                            menu
                                        } else {
                                            menu.copy(items = cachedMenuItems.toPersistentList())
                                        }
                                    }
                                    cachedMenuItems.clear()
                                }

                                is StormfrontUnhandledTagEvent -> {
                                    // debug("Unhandled tag: ${event.tag}")
                                }

                                is StormfrontParseErrorEvent -> {
                                    mainStream.appendLine(
                                        StyledString(
                                            "parse error: ${event.text}",
                                            WarlockStyle.Error
                                        )
                                    )
                                }

                                is StormfrontResourceEvent -> {
                                    flushBuffer(true)
                                    gameCode?.filter { it.isLetter() }?.lowercase()?.let { code ->
                                        val url = "https://www.play.net/bfe/$code-art/${event.picture}.jpg"
                                        logger.debug { "Got resource: $url" }
                                        currentStream.appendResource(url)
                                        if (currentStream == mainStream) {
                                            isPrompting = false
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // This is the strange mode to read books and create characters
                        val buffer = StringBuilder()
                        val c = reader.read()
                        if (c == -1) {
                            // connection was closed by server
                            disconnected()
                            break
                        }
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
                                logComplete { line }
                                logSimple { line }
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
                } catch (e: SocketException) {
                    logger.debug { "Socket exception: " + e.message }
                    disconnected()
                    break
                } catch (e: SocketTimeoutException) {
                    logger.debug { "Socket timeout: " + e.message }
                    disconnected()
                    break
                }
            }
        }
    }

    private fun bufferText(text: StyledString) {
        var styledText = text
        outputStyle?.let { styledText = styledText.applyStyle(it) }
        currentStyle?.let { styledText = styledText.applyStyle(it) }
        styleStack.forEach { styledText = styledText.applyStyle(it) }
        buffer = buffer?.plus(styledText) ?: styledText
    }

    private suspend fun appendToStream(styledText: StyledString, stream: TextStream, ignoreWhenBlank: Boolean) {
        doAppendToStream(styledText, stream, ignoreWhenBlank)
        if (stream == mainStream || ifClosedStream(stream) == mainStream) {
            val text = styledText.toString()
            if (text.isNotBlank()) {
                logSimple { text }
                notifyListeners(ClientTextEvent(text))
            }
        }
    }

    private suspend fun doAppendToStream(styledText: StyledString, stream: TextStream, ignoreWhenBlank: Boolean) {
        if (ignoreWhenBlank && styledText.isBlank())
            return
        stream.appendLine(styledText, ignoreWhenBlank)
        if (stream == mainStream) {
            isPrompting = false
        }
        doIfClosed(stream) { targetStream ->
            val style = windows[stream.id]?.styleIfClosed
            targetStream.appendLine(
                text = style?.let { styledText.applyStyle(WarlockStyle(it)) } ?: styledText,
                ignoreWhenBlank = ignoreWhenBlank
            )
        }
    }

    // TODO: separate buffer into its own class
    private suspend fun flushBuffer(ignoreWhenBlank: Boolean) {
        assert(componentId == null)
        val styledText = buffer ?: StyledString("")
        appendToStream(styledText, currentStream, ignoreWhenBlank)
        buffer = null
    }

    private suspend fun doIfClosed(stream: TextStream, action: suspend (TextStream) -> Unit) {
        if (!openWindows.value.contains(stream.id)) {
            ifClosedStream(stream)?.let { closedStream ->
                action(closedStream)
            }
        }
    }

    private fun ifClosedStream(stream: TextStream): TextStream? {
        val window = windows[stream.id] ?: return null
        val ifClosedName = window.ifClosed ?: "main"
        return if (stream.id != ifClosedName && ifClosedName.isNotBlank()) {
            getStream(ifClosedName)
        } else {
            null
        }
    }

    override suspend fun sendCommand(line: String): SendCommandType =
        withContext(NonCancellable) {
            printCommand(line)
            commandQueue.send(line)
            SendCommandType.COMMAND
        }

    override suspend fun sendCommandDirect(command: String) {
        withContext(writeContext) {
            val toSend = "<c>$command\n"
            try {
                socket?.outputStream?.write(toSend.toByteArray(charset))
                logSimple { ">$command" }
                logComplete { "<command>$command</command>" }
            } catch (e: SocketException) {
                print(StyledString("Could not send command: ${e.message}", WarlockStyle.Error))
            }
        }
    }

    override fun disconnect() {
        doDisconnect()
        scope.launch {
            mainStream.appendLine(StyledString("Closed connection to server."))
        }
    }

    private suspend fun disconnected() {
        if (!disconnected.value) {
            mainStream.appendLine(StyledString("Connection closed by server."))
        }
        doDisconnect()
    }

    private fun doDisconnect() {
        try {
            socket?.close()
        } catch (_: IOException) {
            // Ignore exception
        }
        _disconnected.value = true
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

    suspend fun printCommand(command: String) {
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

    override fun getStream(name: String): TextStream {
        return streamRegistry.getOrCreateStream(name)
    }

    override fun setMaxTypeAhead(value: Int) {
        require(value >= 0)
        maxTypeAhead = value
    }

    private suspend fun notifyListeners(event: ClientEvent) {
        withContext(Dispatchers.IO) {
            _eventFlow.emit(event)
        }
    }

    fun setProxy(process: Process) {
        proxyProcess = process
        process.inputStream.bufferedReader().lineSequence().asFlow()
            .onEach {
                scriptDebug(it)
            }
            .launchIn(scope)
        process.errorStream.bufferedReader().lineSequence().asFlow()
            .onEach {
                doAppendToStream(StyledString(it, listOf(WarlockStyle.Error)), getStream("scriptoutput"), false)
            }
            .launchIn(scope)
    }

    private fun addWindow(window: StormfrontStreamWindow) {
        windows[window.id] = window
        windowRepository.setWindowTitle(
            name = window.id,
            title = window.title,
            subtitle = window.subtitle,
            windowType = WindowType.STREAM,
            showTimestamps = window.timestamp,
        )
    }

    override fun close() {
        scope.cancel()
        if (socket?.isClosed == false) {
            socket?.close()
        }
        proxyProcess?.destroy()
        proxyProcess = null
        _disconnected.value = true
    }

    private suspend fun logComplete(message: () -> String) {
        if (logName != null) {
            fileLogging.logComplete(logName!!, message)
        } else {
            logBuffer.add { fileLogging.logComplete(logName!!, message) }
        }
    }

    private suspend fun logSimple(message: () -> String) {
        if (logName != null) {
            fileLogging.logSimple(logName!!, message)
        } else {
            logBuffer.add { fileLogging.logSimple(logName!!, message) }
        }
    }
}
