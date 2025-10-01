package warlockfe.warlock3.wrayth.network

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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
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
import warlockfe.warlock3.core.client.WarlockProxy
import warlockfe.warlock3.core.client.WarlockSocket
import warlockfe.warlock3.core.compass.DirectionType
import warlockfe.warlock3.core.prefs.repositories.CharacterRepository
import warlockfe.warlock3.core.prefs.repositories.LoggingRepository
import warlockfe.warlock3.core.prefs.repositories.WindowRepository
import warlockfe.warlock3.core.prefs.repositories.defaultMaxTypeAhead
import warlockfe.warlock3.core.text.StyledString
import warlockfe.warlock3.core.text.StyledStringVariable
import warlockfe.warlock3.core.text.WarlockStyle
import warlockfe.warlock3.core.text.isBlank
import warlockfe.warlock3.core.window.StreamRegistry
import warlockfe.warlock3.core.window.TextStream
import warlockfe.warlock3.core.window.WindowType
import warlockfe.warlock3.wrayth.protocol.WraythActionEvent
import warlockfe.warlock3.wrayth.protocol.WraythAppEvent
import warlockfe.warlock3.wrayth.protocol.WraythCastTimeEvent
import warlockfe.warlock3.wrayth.protocol.WraythClearStreamEvent
import warlockfe.warlock3.wrayth.protocol.WraythCliEvent
import warlockfe.warlock3.wrayth.protocol.WraythCompassEndEvent
import warlockfe.warlock3.wrayth.protocol.WraythComponentDefinitionEvent
import warlockfe.warlock3.wrayth.protocol.WraythComponentEndEvent
import warlockfe.warlock3.wrayth.protocol.WraythComponentStartEvent
import warlockfe.warlock3.wrayth.protocol.WraythDataReceivedEvent
import warlockfe.warlock3.wrayth.protocol.WraythDialogDataEvent
import warlockfe.warlock3.wrayth.protocol.WraythDialogObjectEvent
import warlockfe.warlock3.wrayth.protocol.WraythDialogWindowEvent
import warlockfe.warlock3.wrayth.protocol.WraythDirectionEvent
import warlockfe.warlock3.wrayth.protocol.WraythEndCmdList
import warlockfe.warlock3.wrayth.protocol.WraythEolEvent
import warlockfe.warlock3.wrayth.protocol.WraythHandledEvent
import warlockfe.warlock3.wrayth.protocol.WraythMenuEndEvent
import warlockfe.warlock3.wrayth.protocol.WraythMenuItemEvent
import warlockfe.warlock3.wrayth.protocol.WraythMenuStartEvent
import warlockfe.warlock3.wrayth.protocol.WraythModeEvent
import warlockfe.warlock3.wrayth.protocol.WraythNavEvent
import warlockfe.warlock3.wrayth.protocol.WraythOpenUrlEvent
import warlockfe.warlock3.wrayth.protocol.WraythOutputEvent
import warlockfe.warlock3.wrayth.protocol.WraythParseErrorEvent
import warlockfe.warlock3.wrayth.protocol.WraythPopStyleEvent
import warlockfe.warlock3.wrayth.protocol.WraythPromptEvent
import warlockfe.warlock3.wrayth.protocol.WraythPropertyEvent
import warlockfe.warlock3.wrayth.protocol.WraythProtocolHandler
import warlockfe.warlock3.wrayth.protocol.WraythPushCmdEvent
import warlockfe.warlock3.wrayth.protocol.WraythPushStyleEvent
import warlockfe.warlock3.wrayth.protocol.WraythResourceEvent
import warlockfe.warlock3.wrayth.protocol.WraythRoundTimeEvent
import warlockfe.warlock3.wrayth.protocol.WraythSettingsInfoEvent
import warlockfe.warlock3.wrayth.protocol.WraythStartCmdList
import warlockfe.warlock3.wrayth.protocol.WraythStreamEvent
import warlockfe.warlock3.wrayth.protocol.WraythStreamWindowEvent
import warlockfe.warlock3.wrayth.protocol.WraythStyleEvent
import warlockfe.warlock3.wrayth.protocol.WraythTimeEvent
import warlockfe.warlock3.wrayth.protocol.WraythUnhandledTagEvent
import warlockfe.warlock3.wrayth.protocol.WraythUpdateVerbsEvent
import warlockfe.warlock3.wrayth.util.CmdDefinition
import warlockfe.warlock3.wrayth.util.WraythCmd
import warlockfe.warlock3.wrayth.util.WraythStreamWindow
import java.net.SocketException
import java.net.URI
import kotlin.math.max

const val scriptCommandPrefix = '.'
const val clientCommandPrefix = '/'

class WraythClient(
    private val windowRepository: WindowRepository,
    private val characterRepository: CharacterRepository,
    private val streamRegistry: StreamRegistry,
    private val fileLogging: LoggingRepository,
    private val ioDispatcher: CoroutineDispatcher,
    private val socket: WarlockSocket,
) : WarlockClient {

    private val writeContext = ioDispatcher.limitedParallelism(1)

    private val logger = KotlinLogging.logger {}

    private val newLinePattern = Regex("\r?\n")

    private val scope = CoroutineScope(ioDispatcher)

    private var maxTypeAhead: Int = defaultMaxTypeAhead

    private var proxy: WarlockProxy? = null

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

    private val windows = mutableMapOf<String, WraythStreamWindow>()

    private val commandQueue = Channel<String>(Channel.UNLIMITED)
    private val currentTypeAhead = MutableStateFlow(0)

    private var menuCount = 0

    // Line state variables
    private var isPrompting = false
    private var streamBuffer: StyledString? = null
    private var elementBuffer: StyledString? = null

    private val cliCache = mutableListOf<CmdDefinition>()

    private var cliCoords = persistentMapOf<String, CmdDefinition>()

    private val _menuData = MutableStateFlow(WarlockMenuData(0, emptyList()))
    override val menuData: StateFlow<WarlockMenuData> = _menuData.asStateFlow()
    private var currentCmd: WraythCmd? = null

    private var currentMenuId: Int = 0

    private val cachedMenuItems = mutableListOf<WarlockMenuItem>()

    private val openWindows = windowRepository.openWindows
        .stateIn(scope, started = SharingStarted.Eagerly, initialValue = emptyList())

    private var parseText = true

    private var currentStream: TextStream? = null

    private var currentStyle: WarlockStyle? = null
    private val styleStack = ArrayDeque<WarlockStyle>()

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

    init {
        listOf(
            WraythStreamWindow(
                id = "warlockscripts",
                title = "Running scripts",
                subtitle = null,
                ifClosed = "",
                styleIfClosed = null,
                timestamp = false,
            ),
            WraythStreamWindow(
                id = "scriptoutput",
                title = "Script output",
                subtitle = null,
                ifClosed = "main",
                styleIfClosed = "echo",
                timestamp = false,
            ),
            WraythStreamWindow(
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
        characterId.onEach {
            if (it != null) {
                streamRegistry.setCharacterId(it)
            }
        }.launchIn(scope)
    }

    override suspend fun connect(key: String) {
        scope.launch {
            sendCommandDirect(key)
            sendCommandDirect("/FE:WRAYTH /VERSION:1.0.1.28 /P:WIN_UNKNOWN /XML")

            val protocolHandler = WraythProtocolHandler()

            while (!socket.isClosed) {
                try {
                    if (parseText) {
                        // This is the standard wrayth parser
                        val line: String? = socket.readLine()
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
                                WraythHandledEvent -> Unit
                                is WraythModeEvent ->
                                    if (event.id.equals("cmgr", true)) {
                                        parseText = false
                                    }

                                is WraythStreamEvent -> {
                                    componentId = null
                                    flushBuffer(true)
                                    currentStream = getStream(event.id ?: "main")
                                }

                                is WraythClearStreamEvent ->
                                    getStream(event.id).clear()

                                is WraythDataReceivedEvent -> {
                                    bufferText(StyledString(event.text))
                                }

                                is WraythEolEvent -> {
                                    // We're working under the assumption that an end tag is always on the same line as the start tag
                                    flushBuffer(event.ignoreWhenBlank)
                                }

                                is WraythAppEvent -> {
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

                                is WraythOutputEvent ->
                                    outputStyle = event.style

                                is WraythStyleEvent ->
                                    currentStyle = event.style

                                is WraythPushStyleEvent ->
                                    styleStack.addLast(event.style)

                                WraythPopStyleEvent ->
                                    styleStack.removeLastOrNull()

                                is WraythPromptEvent -> {
                                    currentTypeAhead.update { max(0, it - 1) }
                                    styleStack.clear()
                                    componentId = null
                                    currentStyle = null
                                    currentStream = null
                                    if (!isPrompting) {
                                        getMainStream().appendPartial(
                                            StyledString(event.text, listOfNotNull(outputStyle))
                                        )
                                        isPrompting = true
                                    }
                                    notifyListeners(ClientPromptEvent)
                                }

                                is WraythTimeEvent -> {
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

                                is WraythRoundTimeEvent ->
                                    _properties.value += "roundtime" to event.time

                                is WraythCastTimeEvent ->
                                    _properties.value += "casttime" to event.time

                                is WraythSettingsInfoEvent -> {
                                    gameCode = event.instance

                                    // We don't actually handle server settings

                                    // Not 100% where this belongs. connections hang until and empty command is sent
                                    // This must be in response to either mode, playerId, or settingsInfo, so
                                    // we put it here until someone discovers something else
                                    sendCommandDirect("")
                                    sendCommandDirect("_STATE CHATMODE OFF")
                                }

                                is WraythDialogDataEvent -> {
                                    dialogDataId = event.id
                                    if (event.clear && event.id != null) {
                                        notifyListeners(ClientDialogClearEvent(event.id))
                                    }
                                }

                                is WraythDialogObjectEvent -> {
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

                                is WraythCompassEndEvent -> {
                                    notifyListeners(ClientCompassEvent(directions.toPersistentHashSet()))
                                    directions.clear()
                                }

                                is WraythDirectionEvent -> {
                                    directions += event.direction
                                }

                                is WraythPropertyEvent -> {
                                    if (event.value != null)
                                        _properties.value += event.key to event.value
                                    else
                                        _properties.value -= event.key
                                }

                                is WraythComponentDefinitionEvent -> {
                                    // Should not happen on main stream, so don't clear prompt
                                    // TODO: Should currentStyle be used here? is it per stream?
                                    bufferText(
                                        text = StyledString(
                                            persistentListOf(
                                                StyledStringVariable(
                                                    name = event.id,
                                                    styles = styleStack.toPersistentList()
                                                )
                                            )
                                        ),
                                    )
                                    componentId = event.id
                                    elementBuffer = StyledString()
                                }

                                is WraythComponentStartEvent -> {
                                    componentId = event.id
                                    elementBuffer = StyledString()
                                }

                                WraythComponentEndEvent -> {
                                    if (componentId != null) {
                                        // Either replace the component in the map with the new value
                                        //  or remove the component from the map (if we got an empty one)
                                        if (elementBuffer?.substrings.isNullOrEmpty()) {
                                            _components.value -= componentId!!
                                        } else {
                                            _components.value += (componentId!! to elementBuffer!!)
                                        }
                                        val newValue = elementBuffer ?: StyledString()
                                        streamRegistry.getStreams().forEach { stream ->
                                            stream.updateComponent(componentId!!, newValue)
                                        }
                                        elementBuffer = null
                                        componentId = null
                                    } else {
                                        // mismatched component tags?
                                    }
                                }

                                WraythNavEvent -> notifyListeners(ClientNavEvent)

                                is WraythStreamWindowEvent -> {
                                    val window = event.window
                                    if (windows.get(event.window.id) == null && window.id != "main") {
                                        sendCommandDirect("_swclose s${event.window.id}")
                                    }
                                    addWindow(window)
                                }

                                is WraythDialogWindowEvent -> {
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

                                is WraythActionEvent -> {
                                    bufferText(
                                        StyledString(
                                            text = event.text,
                                            style = WarlockStyle.Link(WarlockAction.SendCommand(event.command)),
                                        )
                                    )
                                }

                                is WraythOpenUrlEvent -> {
                                    try {
                                        val url = URI("https://www.play.net/")
                                            .resolve(event.url)
                                        notifyListeners(ClientOpenUrlEvent(url))
                                    } catch (_: Exception) {
                                        // Silently ignore exceptions
                                    }
                                }

                                is WraythUpdateVerbsEvent -> {
                                    sendCommandDirect("_menu update 1")
                                }

                                is WraythStartCmdList -> {
                                    // ignore for now
                                }

                                is WraythEndCmdList -> {
                                    cliCoords = cliCoords.mutate { map ->
                                        cliCache.forEach { cli ->
                                            map[cli.coord] = cli
                                        }
                                    }
                                    cliCache.clear()
                                }

                                is WraythCliEvent -> {
                                    cliCache.add(event.cmd)
                                }

                                is WraythPushCmdEvent -> {
                                    val cmd = event.cmd
                                    if (cmd.coord != null) {
                                        val cmdDef = cliCoords[cmd.coord]
                                        if (cmdDef != null) {
                                            val command = replaceTemplateSymbols(
                                                text = cmdDef.command,
                                                cmdNoun = cmd.noun,
                                                cmdId = cmd.exist,
                                                eventNoun = null,
                                            )
                                            styleStack.addLast(
                                                WarlockStyle.Link(WarlockAction.SendCommand(command))
                                            )
                                        } else {
                                            debug("Could not find cli for coord: ${cmd.coord}")
                                            styleStack.addLast(WarlockStyle(""))
                                        }
                                    } else {
                                        if (cmd.exist != null) {
                                            styleStack.addLast(
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
                                            styleStack.addLast(WarlockStyle(""))
                                        }
                                    }
                                }

                                is WraythMenuStartEvent -> {
                                    event.id?.let {
                                        currentMenuId = it
                                    }
                                }

                                is WraythMenuItemEvent -> {
                                    cliCoords[event.coord]?.let { command ->
                                        val cmd = currentCmd
                                        if (cmd != null) {
                                            cachedMenuItems.add(
                                                WarlockMenuItem(
                                                    label = replaceTemplateSymbols(
                                                        text = command.menu,
                                                        cmdNoun = cmd.noun,
                                                        cmdId = cmd.exist,
                                                        eventNoun = event.noun,
                                                    ),
                                                    category = command.category,
                                                    action = {
                                                        sendCommand(
                                                            replaceTemplateSymbols(
                                                                text = command.command,
                                                                cmdNoun = cmd.noun,
                                                                cmdId = cmd.exist,
                                                                eventNoun = event.noun,
                                                            )
                                                        )
                                                    }
                                                )
                                            )
                                        }
                                    }
                                }

                                is WraythMenuEndEvent -> {
                                    _menuData.update { menu ->
                                        if (menu.id != currentMenuId) {
                                            menu
                                        } else {
                                            menu.copy(items = cachedMenuItems.toPersistentList())
                                        }
                                    }
                                    cachedMenuItems.clear()
                                }

                                is WraythUnhandledTagEvent -> {
                                    // debug("Unhandled tag: ${event.tag}")
                                }

                                is WraythParseErrorEvent -> {
                                    getMainStream().appendLine(
                                        StyledString(
                                            "parse error: ${event.text}",
                                            WarlockStyle.Error
                                        )
                                    )
                                }

                                is WraythResourceEvent -> {
                                    flushBuffer(true)
                                    gameCode?.filter { it.isLetter() }?.lowercase()?.let { code ->
                                        val url = "https://www.play.net/bfe/$code-art/${event.picture}.jpg"
                                        logger.debug { "Got resource: $url" }
                                        (currentStream ?: getMainStream()).appendResource(url)
                                        if (currentStream.isMainStream) {
                                            isPrompting = false
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // This is the strange mode to read books and create characters
                        val buffer = StringBuilder()
                        val c = socket.read()
                        if (c == -1) {
                            // connection was closed by server
                            disconnected()
                            break
                        }
                        val char = c.toChar()
                        buffer.append(char)
                        // check for <mode> tag
                        if (char == '<') {
                            buffer.append(socket.readLine())
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
                            while (socket.ready()) {
                                val c = socket.read()
                                if (c == -1) {
                                    break
                                } else {
                                    buffer.append(c.toChar())
                                }
                            }
                            rawPrint(buffer.toString())
                        }
                    }
                } catch (e: IOException) {
                    logger.debug { "IO exception: " + e.message }
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
        if (elementBuffer != null) {
            elementBuffer = elementBuffer!!.plus(styledText)
        } else {
            streamBuffer = streamBuffer?.plus(styledText) ?: styledText
        }
    }

    private suspend fun appendToStream(styledText: StyledString, stream: TextStream?, ignoreWhenBlank: Boolean) {
        doAppendToStream(styledText, stream, ignoreWhenBlank)
        if (stream.isMainStream || ifClosedStream(stream!!).isMainStream) {
            val text = styledText.toString()
            if (text.isNotBlank()) {
                logSimple { text }
                notifyListeners(ClientTextEvent(text))
            }
        }
    }

    private suspend fun doAppendToStream(styledText: StyledString, stream: TextStream?, ignoreWhenBlank: Boolean) {
        if (ignoreWhenBlank && styledText.isBlank())
            return
        val actualStream = stream ?: getMainStream()
        actualStream.appendLine(styledText, ignoreWhenBlank)
        if (stream.isMainStream) {
            isPrompting = false
        }
        doIfClosed(actualStream) { targetStream ->
            val style = windows[actualStream.id]?.styleIfClosed
            targetStream.appendLine(
                text = style?.let { styledText.applyStyle(WarlockStyle(it)) } ?: styledText,
                ignoreWhenBlank = ignoreWhenBlank
            )
        }
    }

    // TODO: separate buffer into its own class
    private suspend fun flushBuffer(ignoreWhenBlank: Boolean) {
        if (componentId == null) {
            val styledText = streamBuffer ?: StyledString()
            appendToStream(styledText, currentStream, ignoreWhenBlank)
            streamBuffer = null
        }
    }

    private suspend fun doIfClosed(stream: TextStream, action: suspend (TextStream) -> Unit) {
        if (!openWindows.value.contains(stream.id)) {
            ifClosedStream(stream)?.let { closedStream ->
                action(closedStream)
            }
        }
    }

    private suspend fun ifClosedStream(stream: TextStream): TextStream? {
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
            try {
                socket.write("<c>$command\n")

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
            getMainStream().appendLine(StyledString("Closed connection to server."))
        }
    }

    private suspend fun disconnected() {
        if (!disconnected.value) {
            getMainStream().appendLine(StyledString("Connection closed by server."))
        }
        doDisconnect()
    }

    private fun doDisconnect() {
        try {
            socket.close()
        } catch (_: IOException) {
            // Ignore exception
        }
        _disconnected.value = true
    }

    private suspend fun rawPrint(text: String) {
        isPrompting = false
        val lines = text.split(newLinePattern)
        val mainStream = getMainStream()
        lines.dropLast(1).forEach { fullLine ->
            mainStream.appendPartialAndEol(StyledString(fullLine, WarlockStyle.Mono))
        }
        mainStream.appendPartial(StyledString(lines.last(), WarlockStyle.Mono))
    }

    override suspend fun print(message: StyledString) {
        val style = outputStyle
        getMainStream().appendLine(if (style != null) message.applyStyle(style) else message)
        notifyListeners(ClientTextEvent(message.toString()))
    }

    suspend fun printCommand(command: String) {
        val styles = listOfNotNull(outputStyle, WarlockStyle.Command)
        getMainStream().appendPartialAndEol(StyledString(command, styles))
        notifyListeners(ClientTextEvent(command))
    }

    override suspend fun debug(message: String) {
        doAppendToStream(StyledString(message), getStream("debug"), false)
    }

    override suspend fun scriptDebug(message: String) {
        doAppendToStream(StyledString(message), getStream("scriptoutput"), false)
    }

    override suspend fun getStream(name: String): TextStream {
        return streamRegistry.getOrCreateStream(name)
    }

    override fun setMaxTypeAhead(value: Int) {
        require(value >= 0)
        maxTypeAhead = value
    }

    private suspend fun notifyListeners(event: ClientEvent) {
        withContext(ioDispatcher) {
            _eventFlow.emit(event)
        }
    }

    fun setProxy(proxy: WarlockProxy) {
        this.proxy = proxy
        proxy.stdOut
            .onEach {
                scriptDebug(it)
            }
            .catch {
                logger.error(it) { "Error reading stdout" }
            }
            .launchIn(scope)
        proxy.stdErr
            .onEach {
                doAppendToStream(StyledString(it, listOf(WarlockStyle.Error)), getStream("scriptoutput"), false)
            }
            .catch {
                logger.error(it) { "Error reading stderr" }
            }
            .launchIn(scope)
    }

    private fun addWindow(window: WraythStreamWindow) {
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
        if (!socket.isClosed) {
            socket.close()
        }
        proxy?.close()
        proxy = null
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

    private suspend fun getMainStream() = getStream("main")

    private fun replaceTemplateSymbols(text: String, cmdNoun: String?, cmdId: String?, eventNoun: String?): String {
        return text.replace("@", cmdNoun ?: "")
            .replace("#", cmdId?.let { "#$it" } ?: "")
            .replace("%", eventNoun ?: "")
    }
}

val TextStream?.isMainStream
    get() = this == null || this.id == "main"