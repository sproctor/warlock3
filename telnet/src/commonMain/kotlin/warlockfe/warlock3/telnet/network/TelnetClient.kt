package warlockfe.warlock3.telnet.network

import co.touchlab.kermit.Logger
import kotlinx.collections.immutable.toPersistentHashSet
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import warlockfe.warlock3.core.client.ClientCompassEvent
import warlockfe.warlock3.core.client.ClientEvent
import warlockfe.warlock3.core.client.ClientGmcpEvent
import warlockfe.warlock3.core.client.ClientPromptEvent
import warlockfe.warlock3.core.client.ClientTextEvent
import warlockfe.warlock3.core.client.ClientWindowInfoEvent
import warlockfe.warlock3.core.client.GameCharacter
import warlockfe.warlock3.core.client.MudScriptOffer
import warlockfe.warlock3.core.client.PanelObject
import warlockfe.warlock3.core.client.Percentage
import warlockfe.warlock3.core.client.ScriptableClient
import warlockfe.warlock3.core.client.SendCommandType
import warlockfe.warlock3.core.client.WarlockClient
import warlockfe.warlock3.core.client.WarlockMenuData
import warlockfe.warlock3.core.compass.Direction
import warlockfe.warlock3.core.prefs.repositories.CharacterRepository
import warlockfe.warlock3.core.prefs.repositories.LoggingRepository
import warlockfe.warlock3.core.text.StyledString
import warlockfe.warlock3.core.text.WarlockColor
import warlockfe.warlock3.core.text.WarlockStyle
import warlockfe.warlock3.core.util.replaceOrAdd
import warlockfe.warlock3.core.window.TextStream
import warlockfe.warlock3.core.window.WindowInfo
import warlockfe.warlock3.core.window.WindowLocation
import warlockfe.warlock3.core.window.WindowRegistry
import warlockfe.warlock3.core.window.WindowType
import warlockfe.warlock3.telnet.ansi.AnsiParser
import warlockfe.warlock3.telnet.ansi.AnsiSpan
import warlockfe.warlock3.telnet.ansi.toWarlockStyles
import warlockfe.warlock3.telnet.gmcp.GmcpHandler
import warlockfe.warlock3.telnet.gmcp.GmcpUpdate
import warlockfe.warlock3.telnet.gmcp.VitalBar
import warlockfe.warlock3.telnet.gmcp.vitalsPanelObjects
import warlockfe.warlock3.telnet.protocol.StreamingTextDecoder
import warlockfe.warlock3.telnet.protocol.TelnetDecoder
import warlockfe.warlock3.telnet.protocol.TelnetEvent
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/**
 * A [WarlockClient] for a plain telnet MUD: no login protocol of ours, no windows or panels the
 * game can open, just a stream of text with ANSI colour in it that all goes to the main window.
 * A server that speaks GMCP also gets the compass lit from its room exits, a vitals panel built
 * from `Char.Vitals` in the same status-bar slot GS4's minivitals occupy, and the hands filled
 * from what its inventory says is wielded. What it says beyond that, a script can act on: every
 * GMCP message is passed on as a [ClientGmcpEvent], and a MUD may send the script itself, by
 * `Client.GUI` as it would send Mudlet an interface package, which is offered up as [mudScript]
 * unless the connection was made with [acceptScripts] off. As a [ScriptableClient] the client
 * lets such a script set the roundtime and cast time, which nothing in a telnet stream states,
 * the hands and the vitals bars, and flash a window's background.
 *
 * What a MUD does not tell us, the saved connection does. Wrayth names the game and the character
 * in its `<app>` tag; here [gameCode] and [character] come from the connection the user made, and
 * they identify the character the same way, so highlights, macros, scripts and window layouts are
 * saved per character just as they are for a Simutronics game.
 *
 * The one piece of structure to find in the stream is the prompt. A server that negotiates EOR, or
 * sends GA, marks its prompts; for one that does neither, a line the server leaves unfinished for a
 * moment is taken to be one. Scripts wait on prompts, and the input line is echoed onto the prompt
 * line as on a terminal, so this matters more than it looks.
 */
class TelnetClient(
    private val gameCode: String,
    private val character: String,
    private val acceptScripts: Boolean,
    private val characterRepository: CharacterRepository,
    private val windowRegistry: WindowRegistry,
    private val fileLogging: LoggingRepository,
    private val ioDispatcher: CoroutineDispatcher,
    private val socket: TelnetSocket,
) : WarlockClient,
    ScriptableClient {
    private val logger = Logger.withTag("TelnetClient")

    private val scope = CoroutineScope(ioDispatcher)
    private val writeContext = ioDispatcher.limitedParallelism(1)

    private val logName = "${gameCode}_$character"

    private val _eventFlow = MutableSharedFlow<ClientEvent>(extraBufferCapacity = 256)
    override val eventFlow: SharedFlow<ClientEvent> = _eventFlow.asSharedFlow()

    private val _characterId = MutableStateFlow<String?>("$gameCode:$character".lowercase())
    override val characterId: StateFlow<String?> = _characterId.asStateFlow()

    override val gameName: StateFlow<String?> = MutableStateFlow(gameCode)
    override val characterName: StateFlow<String?> = MutableStateFlow(character)

    // Nothing in a telnet stream says how long the roundtime is or what spell is readied; these
    // stay empty, and the UI shows nothing for them, unless a script sets them. The hands are
    // filled from the GMCP inventory when the server has one.
    private val _roundTimeEnd = MutableStateFlow<Long?>(null)
    override val roundTimeEnd: StateFlow<Long?> = _roundTimeEnd.asStateFlow()
    private val _castTimeEnd = MutableStateFlow<Long?>(null)
    override val castTimeEnd: StateFlow<Long?> = _castTimeEnd.asStateFlow()
    private val _leftHand = MutableStateFlow<String?>(null)
    override val leftHand: StateFlow<String?> = _leftHand.asStateFlow()
    private val _rightHand = MutableStateFlow<String?>(null)
    override val rightHand: StateFlow<String?> = _rightHand.asStateFlow()
    private val _spellHand = MutableStateFlow<String?>(null)
    override val spellHand: StateFlow<String?> = _spellHand.asStateFlow()
    override val indicators: StateFlow<Set<String>> = MutableStateFlow(emptySet())
    override val menuData: StateFlow<WarlockMenuData> = MutableStateFlow(WarlockMenuData(0, emptyList()))

    private val _windowInfo = MutableStateFlow<List<WindowInfo>>(emptyList())
    override val windowInfo: StateFlow<List<WindowInfo>> = _windowInfo.asStateFlow()

    private val _disconnected = MutableStateFlow(false)
    override val disconnected: StateFlow<Boolean> = _disconnected.asStateFlow()

    private val _mudScript = MutableStateFlow<MudScriptOffer?>(null)
    override val mudScript: StateFlow<MudScriptOffer?> = _mudScript.asStateFlow()

    private val _handsShown = MutableStateFlow(true)
    override val handsShown: StateFlow<Boolean> = _handsShown.asStateFlow()

    // The bars a script has set, in the order it first set them. Guarded by [vitalsMutex] with
    // the panel they are drawn to, since a script and the GMCP handler may both be writing it.
    private val vitalsMutex = Mutex()
    private val scriptVitals = LinkedHashMap<String, VitalBar>()

    private val telnet = TelnetDecoder()

    // The line being assembled from the stream, guarded by [lineMutex] because the read loop
    // builds it and the command path finishes it (echoing the command onto the prompt line).
    private val lineMutex = Mutex()
    private var line = StyledString()
    private var lineText = StringBuilder()

    // Whether the unfinished line is already on screen, as a prompt or as text that was shown
    // early; further text then extends that line rather than starting one.
    private var partialShown = false

    // Set while the server echoes (a password prompt): what the user types is not shown.
    private var serverEcho = false

    private val windows = mutableMapOf<String, TelnetWindow>()

    private val gmcp = GmcpHandler()

    // Whether the vitals panel has been announced; it is, on the first vitals the server sends.
    private var vitalsAnnounced = false

    // The exits last reported, kept because the compass is told by event and a MUD sends the first
    // room the moment we connect, before anything is listening.
    private var lastExits: Set<Direction>? = null

    init {
        windowRegistry.setCharacterId(checkNotNull(characterId.value))
        scope.launch {
            listOf(
                TelnetWindow(name = "warlockscripts", title = "Running scripts", ifClosed = null, styleIfClosed = null),
                TelnetWindow(name = "scriptoutput", title = "Script output", ifClosed = "main", styleIfClosed = "echo"),
                TelnetWindow(name = "debug", title = "Debug", ifClosed = null, styleIfClosed = null),
            ).forEach { addWindow(it) }
        }
        scope.launch {
            // The game screen subscribes once it is up; a room reported before then would be lost
            // to it, so its exits are sent again.
            _eventFlow.subscriptionCount.first { it > 0 }
            lastExits?.let { notifyListeners(ClientCompassEvent(it.toPersistentHashSet())) }
        }
    }

    override fun getCurrentTime(): Instant = Clock.System.now()

    /** Starts reading. [key] is the Simutronics game key, which a telnet MUD has no use for. */
    override suspend fun connect(key: String) {
        val id = checkNotNull(characterId.value)
        if (characterRepository.getCharacter(id) == null) {
            characterRepository.saveCharacter(GameCharacter(id = id, gameCode = gameCode, name = character))
        }
        scope.launch { readLoop() }
    }

    private suspend fun readLoop() {
        val buffer = ByteArray(8192)
        val text = StreamingTextDecoder()
        val ansi = AnsiParser()
        try {
            while (!socket.isClosed) {
                val count = socket.read(buffer)
                if (count < 0) {
                    lineMutex.withLock {
                        handleSpans(ansi.parse(text.flush()))
                        finishLineIfShown()
                    }
                    disconnected()
                    break
                }
                for (event in telnet.decode(buffer, count)) {
                    when (event) {
                        is TelnetEvent.Data -> lineMutex.withLock { handleSpans(ansi.parse(text.decode(event.bytes))) }
                        TelnetEvent.Prompt -> lineMutex.withLock { promptReceived() }
                        is TelnetEvent.Echo -> serverEcho = event.serverEcho
                        is TelnetEvent.Reply -> socket.write(event.bytes)
                        is TelnetEvent.Gmcp -> handleGmcp(event)
                    }
                }
                // A line the server leaves hanging is a prompt, or as good as one: the input line
                // should sit on it, and a script waiting for a prompt should wake. The wait is
                // for the rest of a line that the network cut in two, which arrives at once.
                if (lineMutex.withLock { lineText.isNotEmpty() && !partialShown } &&
                    !socket.awaitContent(PROMPT_IDLE)
                ) {
                    logger.d { "Taking the hanging line as a prompt" }
                    lineMutex.withLock { promptReceived() }
                }
            }
        } catch (e: IOException) {
            logger.d(e) { "IO exception: " + e.message }
            disconnected()
        }
    }

    private suspend fun handleGmcp(event: TelnetEvent.Gmcp) {
        logger.d { "GMCP ${event.name} ${event.data}" }
        when (val update = gmcp.handle(event.name, event.data)) {
            is GmcpUpdate.Exits -> {
                lastExits = update.directions
                notifyListeners(ClientCompassEvent(update.directions.toPersistentHashSet()))
            }

            is GmcpUpdate.Vitals -> {
                vitalsMutex.withLock { showVitals(update.objects) }
            }

            is GmcpUpdate.Hands -> {
                _leftHand.value = update.left
                _rightHand.value = update.right
            }

            is GmcpUpdate.Script -> {
                if (acceptScripts) {
                    _mudScript.value = update.offer
                } else {
                    logger.d { "Declining the script the MUD offered (version ${update.offer.version})" }
                }
            }

            null -> {}
        }
        // Whatever the client made of it, a script may want it too.
        notifyListeners(ClientGmcpEvent(event.name, event.data))
    }

    override fun setRoundTime(endSeconds: Long?) {
        _roundTimeEnd.value = endSeconds
    }

    override fun setCastTime(endSeconds: Long?) {
        _castTimeEnd.value = endSeconds
    }

    override fun setLeftHand(item: String?) {
        _leftHand.value = item
    }

    override fun setRightHand(item: String?) {
        _rightHand.value = item
    }

    override fun setSpellHand(spell: String?) {
        _spellHand.value = spell
    }

    override fun showHands(shown: Boolean) {
        _handsShown.value = shown
    }

    override suspend fun setVital(
        id: String,
        percent: Int,
        text: String?,
    ) {
        vitalsMutex.withLock {
            scriptVitals[id] = VitalBar(id, Percentage(percent.coerceIn(0, 100)), text ?: id)
            showVitals(vitalsPanelObjects(scriptVitals.values))
        }
    }

    override suspend fun clearVitals() {
        vitalsMutex.withLock {
            scriptVitals.clear()
            showVitals(emptyList())
        }
    }

    override fun flashBackground(
        window: String,
        color: WarlockColor,
        total: Duration,
        fadeIn: Duration,
        fadeOut: Duration,
    ) {
        windowRegistry.flashBackground(window, color, total, fadeIn, fadeOut)
    }

    override suspend fun sendGmcp(
        name: String,
        data: String,
    ) {
        if (!telnet.gmcpNegotiated) {
            logger.d { "Not sending GMCP $name: the server did not negotiate GMCP" }
            return
        }
        withContext(writeContext) {
            try {
                logger.d { "Sending GMCP $name $data" }
                socket.write(TelnetDecoder.gmcpMessage(name, data))
            } catch (e: IOException) {
                print(StyledString("Could not send GMCP message: ${e.message}", WarlockStyle.Error))
            }
        }
    }

    private suspend fun showVitals(objects: List<PanelObject>) {
        if (!vitalsAnnounced) {
            vitalsAnnounced = true
            // The status bar draws whichever panel is announced for it; this is that panel.
            lateinit var info: WindowInfo
            _windowInfo.update { windowInfo ->
                info =
                    WindowInfo(
                        name = VITALS_PANEL,
                        title = "Vitals",
                        subtitle = null,
                        windowType = WindowType.PANEL,
                        showTimestamps = false,
                        backgroundImage = null,
                        location = WindowLocation.STATBAR,
                    )
                windowInfo.replaceOrAdd(info) { it.name == VITALS_PANEL }
            }
            notifyListeners(ClientWindowInfoEvent(info))
        }
        val panel = windowRegistry.getOrCreatePanel(VITALS_PANEL)
        panel.clear()
        objects.forEach { panel.setObject(it) }
        panel.updateState()
    }

    // Under lineMutex.
    private suspend fun handleSpans(spans: List<AnsiSpan>) {
        for (span in spans) {
            val styles = span.style.toWarlockStyles()
            var start = 0
            while (true) {
                val newline = span.text.indexOf('\n', start)
                if (newline < 0) {
                    appendToLine(span.text.substring(start), styles)
                    break
                }
                appendToLine(span.text.substring(start, newline), styles)
                endLine()
                start = newline + 1
            }
        }
    }

    // Under lineMutex.
    private fun appendToLine(
        text: String,
        styles: List<WarlockStyle>,
    ) {
        // Carriage returns are the terminal's business; the line ends at the newline.
        val cleaned = text.replace("\r", "")
        if (cleaned.isEmpty()) return
        lineText.append(cleaned)
        line += StyledString(cleaned, styles)
    }

    // Under lineMutex. The newline arrived: the line goes to the window whole, or finishes the
    // part of it already showing.
    private suspend fun endLine() {
        val styled = line
        val plain = lineText.toString()
        resetLine()
        val mainStream = getMainStream()
        if (partialShown) {
            mainStream.appendPartialAndEol(styled)
            partialShown = false
        } else {
            mainStream.appendLine(styled)
        }
        lineCompleted(plain)
    }

    // Under lineMutex. The server marked a prompt, or went quiet mid-line: what is pending is shown
    // and flagged as a prompt, and anything waiting on a prompt is woken.
    private suspend fun promptReceived() {
        logger.d { "Prompt: pending=\"$lineText\" shown=$partialShown" }
        val mainStream = getMainStream()
        if (lineText.isNotEmpty() || partialShown) {
            val styled = line
            line = StyledString()
            // The text stays in lineText so the prompt line is logged when the command finishes it.
            mainStream.appendPartial(styled, isPrompt = true)
            partialShown = true
        }
        notifyListeners(ClientPromptEvent)
    }

    // Under lineMutex. Ends whatever partial line is on screen, so the next text starts a new one.
    private suspend fun finishLineIfShown() {
        if (!partialShown) return
        val plain = lineText.toString()
        resetLine()
        getMainStream().appendPartialAndEol(StyledString())
        partialShown = false
        lineCompleted(plain)
    }

    private fun resetLine() {
        line = StyledString()
        lineText = StringBuilder()
    }

    private suspend fun lineCompleted(plain: String) {
        logSimple { plain }
        logComplete { plain }
        if (plain.isNotBlank()) {
            notifyListeners(ClientTextEvent(plain))
        }
    }

    override suspend fun sendCommand(line: String): SendCommandType =
        withContext(NonCancellable) {
            printCommand(line)
            sendCommandDirect(line)
            SendCommandType.COMMAND
        }

    override suspend fun sendCommandDirect(command: String) {
        withContext(writeContext) {
            try {
                logger.d { "Writing command: $command" }
                socket.write("$command\r\n".encodeToByteArray())
                logSimple { ">$command" }
                logComplete { ">$command" }
            } catch (e: IOException) {
                print(StyledString("Could not send command: ${e.message}", WarlockStyle.Error))
            }
        }
    }

    override suspend fun sendWidgetCommand(
        command: String,
        echo: String?,
    ) {
        echo?.let { printCommand(it) }
        sendCommandDirect(command)
    }

    /** A telnet MUD has no context menus; the request is answered with an empty menu. */
    override fun requestMenu(
        exist: String,
        noun: String?,
    ): Int = 0

    /**
     * Shows the command on the prompt line, as a terminal would, and ends the line. While the
     * server has taken over echoing (a password prompt), the command is not shown, and only the
     * line is ended.
     */
    private suspend fun printCommand(command: String) {
        withContext(ioDispatcher) {
            lineMutex.withLock {
                val plain = lineText.toString()
                resetLine()
                val mainStream = getMainStream()
                val echoed = if (serverEcho) StyledString() else StyledString(command, WarlockStyle.Command)
                mainStream.appendPartialAndEol(echoed)
                partialShown = false
                if (plain.isNotEmpty()) {
                    logSimple { plain }
                    logComplete { plain }
                }
            }
            if (!serverEcho) {
                notifyListeners(ClientTextEvent(command))
            }
        }
    }

    override suspend fun print(message: StyledString) {
        withContext(ioDispatcher) {
            lineMutex.withLock {
                finishLineIfShown()
                getMainStream().appendLine(message)
            }
            notifyListeners(ClientTextEvent(message.toText()))
        }
    }

    override suspend fun debug(message: String) {
        appendToStream(StyledString(message), getStream("debug"))
    }

    override suspend fun scriptDebug(message: String) {
        appendToStream(StyledString(message), getStream("scriptoutput"))
    }

    private suspend fun appendToStream(
        styledText: StyledString,
        stream: TextStream,
    ) {
        stream.appendLine(styledText)
        // A closed window's text shows in the window it names instead, in that window's style.
        windows[stream.id]?.ifClosed?.let { ifClosed ->
            val style = windows[stream.id]?.styleIfClosed
            getStream(ifClosed).appendLine(
                text = style?.let { styledText.applyStyle(WarlockStyle(it)) } ?: styledText,
                showWhenClosed = stream.id,
            )
        }
    }

    override suspend fun getStream(name: String): TextStream = windowRegistry.getOrCreateStream(name)

    private suspend fun getMainStream(): TextStream = getStream("main")

    override fun getComponents(): Map<String, StyledString> = emptyMap()

    override fun getComponent(name: String): StyledString? = null

    /** Type-ahead is a Simutronics concept: without a prompt per command there is nothing to count. */
    override fun setMaxTypeAhead(value: Int) {}

    override fun disconnect() {
        doDisconnect()
        scope.launch {
            getMainStream().appendLine(StyledString("Closed connection to server."))
        }
    }

    private suspend fun disconnected() {
        if (!disconnected.value) {
            getMainStream().appendLine(
                StyledString(text = "Connection closed by server.", style = WarlockStyle.Echo),
            )
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

    override fun close() {
        scope.cancel()
        if (!socket.isClosed) {
            socket.close()
        }
        _disconnected.value = true
    }

    private suspend fun notifyListeners(event: ClientEvent) {
        _eventFlow.emit(event)
    }

    private suspend fun logSimple(message: () -> String) {
        fileLogging.logSimple(logName, message)
    }

    private suspend fun logComplete(message: () -> String) {
        fileLogging.logComplete(logName, message)
    }

    private suspend fun addWindow(window: TelnetWindow) {
        val stream = getStream(window.name)
        stream.showTimestamps(false)
        stream.setApplyStyling(false)
        windows[window.name] = window
        lateinit var info: WindowInfo
        _windowInfo.update { windowInfo ->
            val existing = windowInfo.firstOrNull { it.name == window.name }
            info =
                WindowInfo(
                    name = window.name,
                    title = window.title,
                    subtitle = null,
                    windowType = WindowType.STREAM,
                    showTimestamps = false,
                    backgroundImage = existing?.backgroundImage,
                )
            windowInfo.replaceOrAdd(info) { it.name == window.name }
        }
        notifyListeners(ClientWindowInfoEvent(info))
    }

    private data class TelnetWindow(
        val name: String,
        val title: String,
        val ifClosed: String?,
        val styleIfClosed: String?,
    )

    private companion object {
        const val VITALS_PANEL = "vitals"

        // How long a line may hang before it is taken for a prompt. Long enough for the rest of a
        // line the network split to arrive, short enough that the input line lands on a prompt
        // before the user has finished reading it.
        val PROMPT_IDLE = 100.milliseconds
    }
}
