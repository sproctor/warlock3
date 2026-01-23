package warlockfe.warlock3.wrayth.network

import com.eygraber.uri.Uri
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import warlockfe.warlock3.core.client.ClientCompassEvent
import warlockfe.warlock3.core.client.ClientEvent
import warlockfe.warlock3.core.client.ClientNavEvent
import warlockfe.warlock3.core.client.ClientOpenUrlEvent
import warlockfe.warlock3.core.client.ClientPromptEvent
import warlockfe.warlock3.core.client.ClientTextEvent
import warlockfe.warlock3.core.client.ClientWindowInfoEvent
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
import warlockfe.warlock3.core.prefs.repositories.defaultMaxTypeAhead
import warlockfe.warlock3.core.text.StyledString
import warlockfe.warlock3.core.text.StyledStringVariable
import warlockfe.warlock3.core.text.WarlockStyle
import warlockfe.warlock3.core.text.isBlank
import warlockfe.warlock3.core.util.getIgnoringCase
import warlockfe.warlock3.core.util.replaceOrAdd
import warlockfe.warlock3.core.window.TextStream
import warlockfe.warlock3.core.window.WindowInfo
import warlockfe.warlock3.core.window.WindowRegistry
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
import warlockfe.warlock3.wrayth.protocol.WraythIndicatorEvent
import warlockfe.warlock3.wrayth.protocol.WraythLeftEvent
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
import warlockfe.warlock3.wrayth.protocol.WraythProtocolHandler
import warlockfe.warlock3.wrayth.protocol.WraythPushCmdEvent
import warlockfe.warlock3.wrayth.protocol.WraythPushStyleEvent
import warlockfe.warlock3.wrayth.protocol.WraythResourceEvent
import warlockfe.warlock3.wrayth.protocol.WraythRightEvent
import warlockfe.warlock3.wrayth.protocol.WraythRoundTimeEvent
import warlockfe.warlock3.wrayth.protocol.WraythSettingsInfoEvent
import warlockfe.warlock3.wrayth.protocol.WraythSpellEvent
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
import warlockfe.warlock3.wrayth.util.resolve
import kotlin.math.max
import kotlin.time.Clock

private val baseUri = Uri.parse("https://www.play.net/")

class WraythClient(
    private val characterRepository: CharacterRepository,
    private val windowRegistry: WindowRegistry,
    private val fileLogging: LoggingRepository,
    private val ioDispatcher: CoroutineDispatcher,
    private val socket: WarlockSocket,
) : WarlockClient {

    private val writeContext = ioDispatcher.limitedParallelism(1)

    private val logger = KotlinLogging.logger {}

    private val newLinePattern = Regex("\r?\n")

    private val scope = CoroutineScope(ioDispatcher)

    // Settings
    private var maxTypeAhead: Int = defaultMaxTypeAhead

    private var proxy: WarlockProxy? = null

    private var gameCode: String? = null

    private val _eventFlow = MutableSharedFlow<ClientEvent>()
    override val eventFlow: SharedFlow<ClientEvent> = _eventFlow.asSharedFlow()

    private val _characterId = MutableStateFlow<String?>(null)
    override val characterId: StateFlow<String?> = _characterId.asStateFlow()

    private val _roundTime = MutableStateFlow<Long?>(null)
    override val roundTime = _roundTime.asStateFlow()

    private val _castTime = MutableStateFlow<Long?>(null)
    override val castTime = _castTime.asStateFlow()

    private val _gameName = MutableStateFlow<String?>(null)
    override val gameName = _gameName.asStateFlow()

    private val _characterName = MutableStateFlow<String?>(null)
    override val characterName = _characterName.asStateFlow()

    private val _leftHand = MutableStateFlow<String?>(null)
    override val leftHand = _leftHand.asStateFlow()

    private val _rightHand = MutableStateFlow<String?>(null)
    override val rightHand = _rightHand.asStateFlow()

    private val _spellHand = MutableStateFlow<String?>(null)
    override val spellHand = _spellHand.asStateFlow()

    private val _indicators = MutableStateFlow<Set<String>>(emptySet())
    override val indicators = _indicators.asStateFlow()

    private val componentsMutex = Mutex()
    private var components = persistentMapOf<String, StyledString>()

    private val logBuffer = mutableListOf<suspend () -> Unit>()
    private var logName: String? = null

    private val windows = mutableMapOf<String, WraythStreamWindow>()

    private val _windowInfo = MutableStateFlow<List<WindowInfo>>(emptyList())
    override val windowInfo = _windowInfo.asStateFlow()

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
        get() = Clock.System.now().toEpochMilliseconds() + delta

    init {
        scope.launch {
            listOf(
                WraythStreamWindow(
                    name = "warlockscripts",
                    title = "Running scripts",
                    subtitle = null,
                    ifClosed = null,
                    styleIfClosed = null,
                    timestamp = false,
                ),
                WraythStreamWindow(
                    name = "scriptoutput",
                    title = "Script output",
                    subtitle = null,
                    ifClosed = "main",
                    styleIfClosed = "echo",
                    timestamp = false,
                ),
                WraythStreamWindow(
                    name = "debug",
                    title = "Debug",
                    subtitle = null,
                    ifClosed = null,
                    styleIfClosed = null,
                    timestamp = false,
                ),
            ).forEach { addWindow(it) }
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
                windowRegistry.setCharacterId(it)
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
                                    if (game != null && character != null) {
                                        val characterId = "${event.game}:${event.character}".lowercase()
                                        _characterId.value = characterId
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
                                        _gameName.value = game
                                        _characterName.value = character
                                    }
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
                                            StyledString(event.text, listOfNotNull(outputStyle)),
                                            isPrompt = true
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
                                        delta = newTime - Clock.System.now().toEpochMilliseconds() - 1000L
                                    } else if (newTime < currentTime - 1000L) {
                                        // We're more than 1s fast
                                        delta = newTime - Clock.System.now().toEpochMilliseconds() + 1000L
                                    }
                                }

                                is WraythRoundTimeEvent ->
                                    event.time.toLongOrNull()?.let {
                                        _roundTime.value = it
                                    }

                                is WraythCastTimeEvent ->
                                    event.time.toLongOrNull()?.let {
                                        _castTime.value = it
                                    }

                                is WraythIndicatorEvent -> {
                                    if (event.visible) {
                                        _indicators.update { it + event.iconId }
                                    } else {
                                        _indicators.update { it - event.iconId }
                                    }
                                }

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
                                    if (event.id == null) {
                                        dialogDataId?.let { windowRegistry.getOrCreateDialog(it).updateState() }
                                    }
                                    dialogDataId = event.id
                                    if (event.clear && event.id != null) {
                                        this@WraythClient.windowRegistry.getOrCreateDialog(event.id).clear()
                                    }
                                }

                                is WraythDialogObjectEvent -> {
                                    // TODO: record this data somewhere
                                    // val data = event.data
                                    //if (data is DialogObject.ProgressBar) {
                                    //    _properties.value = _properties.value +
                                    //(data.id to data.value.value.toString()) +
                                    //            ((data.id + "text") to (data.text ?: ""))
                                    //}
                                    dialogDataId?.let {
                                        windowRegistry.getOrCreateDialog(it).setObject(event.data)
                                    }
                                }

                                is WraythCompassEndEvent -> {
                                    notifyListeners(ClientCompassEvent(directions.toPersistentHashSet()))
                                    directions.clear()
                                }

                                is WraythDirectionEvent -> {
                                    directions += event.direction
                                }

                                is WraythLeftEvent ->
                                    _leftHand.value = event.value

                                is WraythRightEvent ->
                                    _rightHand.value = event.value

                                is WraythSpellEvent ->
                                    _spellHand.value = event.value

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
                                        componentsMutex.withLock {
                                            components = if (elementBuffer?.substrings.isNullOrEmpty()) {
                                                components.remove(componentId!!)
                                            } else {
                                                components.put(componentId!!, elementBuffer!!)
                                            }
                                        }
                                        val newValue = elementBuffer ?: StyledString()
                                        windowRegistry.getStreams().forEach { stream ->
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
                                    if (windows[event.window.name] == null && window.name != "main") {
                                        sendCommandDirect("_swclose s${event.window.name}")
                                    }
                                    addWindow(window)
                                }

                                is WraythDialogWindowEvent -> {
                                    val window = event.window
                                    if (window.resident) {
                                        val window = WindowInfo(
                                            name = window.id,
                                            title = window.title,
                                            subtitle = null,
                                            windowType = WindowType.DIALOG,
                                            showTimestamps = false,
                                        )
                                        _windowInfo.update { windowInfo ->
                                            windowInfo.replaceOrAdd(window) { it.name == window.name }
                                        }
                                        notifyListeners(ClientWindowInfoEvent(window))
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
                                        val url = resolve(baseUri, Uri.parse(event.url))
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
                                        val action = WarlockAction.SendCommandWithLookup {
                                            val cmdDef = cliCoords[cmd.coord]
                                            if (cmdDef != null) {
                                                replaceTemplateSymbols(
                                                    text = cmdDef.command,
                                                    cmdNoun = cmd.noun,
                                                    cmdId = cmd.exist,
                                                    eventNoun = null,
                                                )
                                            } else {
                                                print(
                                                    StyledString(
                                                        "Could not find cli for coord: ${cmd.coord}",
                                                        WarlockStyle.Error
                                                    )
                                                )
                                                ""
                                            }
                                        }
                                        styleStack.addLast(
                                            WarlockStyle.Link(action)
                                        )
                                    } else if (cmd.exist != null) {
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
                        val text = socket.readAvailable()
                        // check for <mode> tag
                        val sections = text.split(modeRegex, limit = 2)
                        if (sections.size > 1) {
                            rawPrint(sections[0])
                            parseText = true
                            protocolHandler.parseLine(sections[1])
                        } else {
                            rawPrint(text)
                        }
                    }
                } catch (e: IOException) {
                    logger.debug(e) { "IO exception: " + e.message }
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

    private suspend fun appendToStream(styledText: StyledString, stream: TextStream, ignoreWhenBlank: Boolean) {
        doAppendToStream(styledText, stream, ignoreWhenBlank)
        if (stream.isMainStream || windows[stream.id]?.ifClosed == "main") {
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
        if (stream.isMainStream) {
            isPrompting = false
        } else {
            // send text to `ifClosed` stream, if stream isn't main
            windows[stream.id]?.ifClosed?.let { ifClosed ->
                val ifClosedStream = getStream(ifClosed)
                if (ifClosedStream.isMainStream) {
                    isPrompting = false
                }
                val style = windows[stream.id]?.styleIfClosed
                ifClosedStream.appendLine(
                    text = style?.let { styledText.applyStyle(WarlockStyle(it)) } ?: styledText,
                    ignoreWhenBlank = ignoreWhenBlank,
                    showWhenClosed = stream.id,
                )
            }
        }
    }

    // TODO: separate buffer into its own class
    private suspend fun flushBuffer(ignoreWhenBlank: Boolean) {
        if (componentId == null) {
            val styledText = streamBuffer ?: StyledString()
            appendToStream(styledText, currentStream ?: getMainStream(), ignoreWhenBlank)
            streamBuffer = null
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
                logger.debug { "Writing command: $command" }
                socket.write("<c>$command\n")

                logSimple { ">$command" }
                logComplete { "<command>$command</command>" }
            } catch (e: IOException) {
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
        logComplete { text }
        logSimple { text }
        isPrompting = false
        val lines = text.split(newLinePattern)
        val mainStream = getMainStream()
        lines.dropLast(1).forEach { fullLine ->
            mainStream.appendPartialAndEol(StyledString(fullLine, WarlockStyle.Mono))
        }
        mainStream.appendPartial(StyledString(lines.last(), WarlockStyle.Mono), isPrompt = false)
    }

    override suspend fun print(message: StyledString) {
        withContext(ioDispatcher) {
            val style = outputStyle
            getMainStream().appendLine(if (style != null) message.applyStyle(style) else message)
            notifyListeners(ClientTextEvent(message.toString()))
        }
    }

    suspend fun printCommand(command: String) {
        withContext(ioDispatcher) {
            val styles = listOfNotNull(outputStyle, WarlockStyle.Command)
            getMainStream().appendPartialAndEol(StyledString(command, styles))
            notifyListeners(ClientTextEvent(command))
        }
    }

    override suspend fun debug(message: String) {
        doAppendToStream(StyledString(message), getStream("debug"), false)
    }

    override suspend fun scriptDebug(message: String) {
        doAppendToStream(StyledString(message), getStream("scriptoutput"), false)
    }

    override suspend fun getStream(name: String): TextStream {
        return windowRegistry.getOrCreateStream(name)
    }

    override fun setMaxTypeAhead(value: Int) {
        maxTypeAhead = value.coerceAtLeast(0)
    }

    private suspend fun notifyListeners(event: ClientEvent) {
        _eventFlow.emit(event)
    }

    fun setProxy(proxy: WarlockProxy) {
        this.proxy = proxy
        proxy.stdOut
            .onEach {
                logger.debug { "Proxy output: $it" }
                scriptDebug(it)
            }
            .catch {
                logger.error(it) { "Error reading stdout" }
            }
            .launchIn(scope)
        proxy.stdErr
            .onEach {
                logger.debug { "Proxy error: $it" }
                doAppendToStream(StyledString(it, listOf(WarlockStyle.Error)), getStream("scriptoutput"), false)
            }
            .catch {
                logger.error(it) { "Error reading stderr" }
            }
            .launchIn(scope)
    }

    private suspend fun addWindow(window: WraythStreamWindow) {
        val stream = getStream(window.name)
        stream.showTimestamps(window.timestamp)
        windows[window.name] = window
        val info = WindowInfo(
            name = window.name,
            title = window.title,
            subtitle = window.subtitle,
            windowType = WindowType.STREAM,
            showTimestamps = window.timestamp,
        )
        _windowInfo.update { windowInfo ->
            windowInfo.replaceOrAdd(info) { it.name == window.name }
        }
        notifyListeners(
            ClientWindowInfoEvent(info)
        )
    }

    override fun getComponents(): Map<String, StyledString> {
        return components
    }

    override fun getComponent(name: String): StyledString? {
        return components.getIgnoringCase(name)
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

    companion object {
        private val modeRegex = Regex("(?=<mode)")
    }
}

val TextStream?.isMainStream
    get() = this == null || this.id == "main"