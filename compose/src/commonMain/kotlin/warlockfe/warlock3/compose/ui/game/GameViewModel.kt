package warlockfe.warlock3.compose.ui.game

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.delete
import androidx.compose.foundation.text.input.selectAll
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.text.TextRange
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.io.files.Path
import warlockfe.warlock3.compose.components.DEFAULT_PANEL_SCALE
import warlockfe.warlock3.compose.ui.window.ComposePanelState
import warlockfe.warlock3.compose.ui.window.StreamWindowData
import warlockfe.warlock3.compose.ui.window.WindowFindController
import warlockfe.warlock3.compose.ui.window.WindowSelectionController
import warlockfe.warlock3.compose.ui.window.WindowUiState
import warlockfe.warlock3.compose.util.SafeClipboard
import warlockfe.warlock3.compose.util.clipEntryOf
import warlockfe.warlock3.compose.util.insertReplacingSelection
import warlockfe.warlock3.compose.util.openUrl
import warlockfe.warlock3.compose.util.plainText
import warlockfe.warlock3.core.client.ClientCloseWindowEvent
import warlockfe.warlock3.core.client.ClientCompassEvent
import warlockfe.warlock3.core.client.ClientEvent
import warlockfe.warlock3.core.client.ClientOpenUrlEvent
import warlockfe.warlock3.core.client.ClientOpenWindowEvent
import warlockfe.warlock3.core.client.ClientWindowInfoEvent
import warlockfe.warlock3.core.client.GameCharacter
import warlockfe.warlock3.core.client.HandBlock
import warlockfe.warlock3.core.client.PanelObject
import warlockfe.warlock3.core.client.ScriptableClient
import warlockfe.warlock3.core.client.WarlockAction
import warlockfe.warlock3.core.client.WarlockClient
import warlockfe.warlock3.core.compass.Direction
import warlockfe.warlock3.core.macro.MacroHandler
import warlockfe.warlock3.core.macro.ScrollEvent
import warlockfe.warlock3.core.prefs.CompassStyle
import warlockfe.warlock3.core.prefs.models.Action
import warlockfe.warlock3.core.prefs.models.ActionBar
import warlockfe.warlock3.core.prefs.repositories.ActionRepository
import warlockfe.warlock3.core.prefs.repositories.AliasRepository
import warlockfe.warlock3.core.prefs.repositories.CharacterSettingsRepository
import warlockfe.warlock3.core.prefs.repositories.ClientSettingRepository
import warlockfe.warlock3.core.prefs.repositories.CommandHistoryRepository
import warlockfe.warlock3.core.prefs.repositories.ConnectionRepository
import warlockfe.warlock3.core.prefs.repositories.DEFAULT_MAX_TYPE_AHEAD
import warlockfe.warlock3.core.prefs.repositories.MAX_TYPE_AHEAD_KEY
import warlockfe.warlock3.core.prefs.repositories.MacroRepository
import warlockfe.warlock3.core.prefs.repositories.PresetRepository
import warlockfe.warlock3.core.prefs.repositories.ProgressBarSettingRepository
import warlockfe.warlock3.core.prefs.repositories.SCRIPT_COMMAND_PREFIX_KEY
import warlockfe.warlock3.core.prefs.repositories.VariableRepository
import warlockfe.warlock3.core.prefs.repositories.WindowSettingsRepository
import warlockfe.warlock3.core.script.MudScriptStore
import warlockfe.warlock3.core.script.ScriptManager
import warlockfe.warlock3.core.text.Alias
import warlockfe.warlock3.core.text.FontConfig
import warlockfe.warlock3.core.text.StyleDefinition
import warlockfe.warlock3.core.text.WarlockColor
import warlockfe.warlock3.core.window.WindowLocation
import warlockfe.warlock3.core.window.WindowRegistry
import kotlin.time.Instant

/**
 * One connected game's screen. The parts with state of their own live in their own classes, which
 * this one owns and wires together: [CommandHistory], [WindowFinder], [MacroRunner],
 * [CommandHandler], [ScriptControl], [ScriptLauncher], [GameWindows], [PhoneTabs] and [DockLayoutStore].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModel(
    private val windowSettingsRepository: WindowSettingsRepository,
    private val client: WarlockClient,
    val macroRepository: MacroRepository,
    val variableRepository: VariableRepository,
    private val presetRepository: PresetRepository,
    private val scriptManager: ScriptManager,
    val characterSettingsRepository: CharacterSettingsRepository,
    aliasRepository: AliasRepository,
    actionRepository: ActionRepository,
    private val windowRegistry: WindowRegistry,
    private val progressBarSettingRepository: ProgressBarSettingRepository,
    private val clientSettingRepository: ClientSettingRepository,
    commandHistoryRepository: CommandHistoryRepository,
    private val connectionRepository: ConnectionRepository,
    mudScriptStore: MudScriptStore,
    private val ioDispatcher: CoroutineDispatcher,
    private val reconnectAction: (suspend () -> Unit)? = null,
    // Told the names of the scripts a reconnect stopped, once the game that replaced this one is up.
    private val onReconnectStoppedScripts: suspend (names: List<String>) -> Unit = {},
) : ViewModel(),
    MacroHandler {
    private val logger = Logger.withTag("GameViewModel")

    val entryTextState = TextFieldState()

    override val entryText: CharSequence
        get() = entryTextState.text

    private val _scrollEvents = MutableStateFlow<PersistentList<ScrollEvent>>(persistentListOf())
    val scrollEvents = _scrollEvents.asStateFlow()

    private val _compassState = MutableStateFlow(emptySet<Direction>())
    val compassState = _compassState.asStateFlow()

    // Compass display style (button grid vs skin rose). Persisted client-wide via client.toml so it
    // survives restarts; the right-click compass menu reads and updates it.
    val compassStyle =
        clientSettingRepository
            .observeCompassStyle()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = CompassStyle.BUTTONS,
            )

    fun setCompassStyle(style: CompassStyle) {
        viewModelScope.launch {
            clientSettingRepository.putCompassStyle(style)
        }
    }

    /**
     * The widgets of whichever panel the game put in the status bar, which we draw as chrome rather
     * than as a window. The game names the panel (`minivitals` in both GS4 and DR, `vitals` on a
     * telnet MUD) but the name is not the contract: `location='statBar'` is, so we follow the
     * location. Empty until one is announced.
     */
    val vitalBars: StateFlow<List<PanelObject>> =
        client.windowInfo
            .map { infos -> infos.firstOrNull { it.location == WindowLocation.STATBAR }?.name }
            .distinctUntilChanged()
            .flatMapLatest { name ->
                name?.let { (windowRegistry.getOrCreatePanel(it) as ComposePanelState).objects }
                    ?: flowOf(emptyList())
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = emptyList(),
            )

    val indicators = client.indicators
    val leftHand = client.leftHand
    val rightHand = client.rightHand
    val spellHand = client.spellHand

    /** Whether to draw the hands row at all: a MUD's script may hide it, a Simutronics game always has it. */
    val handsShown: StateFlow<Boolean> = (client as? ScriptableClient)?.handsShown ?: MutableStateFlow(true)

    /** The blocks of the hands row, in order: a MUD's script may hide, add to and rearrange them. */
    val handBlocks: StateFlow<List<HandBlock>> = (client as? ScriptableClient)?.handBlocks ?: MutableStateFlow(HandBlock.HANDS)

    val character =
        combine(
            client.characterId,
            client.gameName,
            client.characterName,
        ) { characterId, game, character ->
            if (characterId != null && game != null && character != null) {
                GameCharacter(id = characterId, gameCode = game, name = character)
            } else {
                null
            }
        }

    // The connection's custom window title, or null to fall back to the character name. Reactive, so
    // editing the connection while connected updates the title live.
    val windowTitle: StateFlow<String?> =
        client.characterId
            .observePerCharacter { characterId ->
                connectionRepository.observeWindowTitle(characterId)
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.Lazily,
                initialValue = null,
            )

    val windowSettings =
        client.characterId
            .observePerCharacter { characterId ->
                windowSettingsRepository.observeWindowSettings(characterId)
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.Lazily,
                initialValue = emptyList(),
            )

    val progressBarSettings =
        client.characterId
            .observePerCharacter { characterId ->
                progressBarSettingRepository
                    .observeByCharacter(characterId)
                    .map { settings -> settings.associateBy { it.id } }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.Lazily,
                initialValue = emptyMap(),
            )

    val openWindows =
        windowSettings.map { currentWindowSettings ->
            currentWindowSettings.mapNotNull { entity ->
                entity.takeIf { it.open }?.name
            }
        }

    val windows = client.windowInfo

    // The windows a character owns: the ones that can be listed, shown, hidden and given saved
    // settings. Transient panels the game opened stay out of it, so they never reach the window list
    // or the settings tree - the game is the only thing that opens and closes them.
    val residentWindows =
        windows
            .map { infos -> infos.filter { it.resident } }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = emptyList(),
            )

    /** The windows that may become a phone tab. */
    val tabbableWindows =
        windows
            .map { it.tabbable() }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = emptyList(),
            )

    /** The phone tab strip: which windows have a tab, in what order. */
    internal val phoneTabs = PhoneTabs(client.characterId, characterSettingsRepository, tabbableWindows, viewModelScope)

    /** Where the docking bridge saves and loads the character's window arrangement. */
    internal val dockLayout = DockLayoutStore(client.characterId, characterSettingsRepository)

    // The connected character's id, used by the settings dialog to decide whether its live window
    // info (titles, hidden-window list) applies to the character being edited.
    val connectedCharacterId: StateFlow<String?> get() = client.characterId

    // One-shot request to open the settings dialog on Appearance with a given window's editor focused,
    // fired by a window's header/context-menu "Window settings" action. A SharedFlow (not a StateFlow)
    // because it's a navigation event that must not linger and re-fire when settings is next opened.
    private val _editWindowSettingsRequests =
        MutableSharedFlow<String>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val editWindowSettingsRequests: SharedFlow<String> = _editWindowSettingsRequests.asSharedFlow()

    fun requestEditWindowSettings(name: String) {
        _editWindowSettingsRequests.tryEmit(name)
    }

    val scriptCommandPrefix =
        client.characterId
            .observePerCharacter { characterId ->
                characterSettingsRepository
                    .observe(characterId = characterId, key = SCRIPT_COMMAND_PREFIX_KEY)
                    .map { it ?: "." }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = ".",
            )

    private val aliases: StateFlow<List<Alias>> =
        client.characterId
            .observePerCharacter { characterId ->
                aliasRepository.observeForCharacter(characterId)
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = emptyList(),
            )

    // The configured action buttons for the current character (merged with global): the resolved
    // toolbar to draw plus the full pool so a group's children can be looked up by id.
    val actionBar: StateFlow<ActionBar> =
        client.characterId
            .observePerCharacter { characterId ->
                actionRepository.observeForCharacter(characterId)
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.Lazily,
                initialValue = ActionBar.EMPTY,
            )

    val presets = windowRegistry.presets

    // The resolved base ("default text") style: colors + font + weight + italic/underline, cascaded
    // skin -> global -> character. Feeds the window base style + font and the input/status chrome.
    val baseStyle = windowRegistry.baseStyle

    /** The character's panel font; used by panel widgets without a per-window override. */
    val panelFont: StateFlow<FontConfig?> =
        client.characterId
            .observePerCharacter { characterSettingsRepository.observePanelFont(it) }
            .stateIn(scope = viewModelScope, started = SharingStarted.Eagerly, initialValue = null)

    /** The character's panel scale; multiplies pixel-sized panel geometry without a per-window override. */
    val panelScale: StateFlow<Float> =
        client.characterId
            .observePerCharacter { characterSettingsRepository.observePanelScale(it) }
            .map { it ?: DEFAULT_PANEL_SCALE }
            .stateIn(scope = viewModelScope, started = SharingStarted.Eagerly, initialValue = DEFAULT_PANEL_SCALE)

    /** The background flashes scripts have asked of windows; the windows' views play them. */
    val backgroundFlashes = windowRegistry.backgroundFlashes

    val roundTimeEnd =
        client.roundTimeEnd
            .map { roundTime ->
                val now = getCurrentTime()
                roundTime?.let { Instant.fromEpochSeconds(it, now.nanosecondsOfSecond) }
            }.stateIn(scope = viewModelScope, started = SharingStarted.Eagerly, initialValue = null)

    val castTimeEnd =
        client.castTimeEnd
            .map { castTime ->
                val now = getCurrentTime()
                castTime?.let { Instant.fromEpochSeconds(it, now.nanosecondsOfSecond) }
            }.stateIn(scope = viewModelScope, started = SharingStarted.Eagerly, initialValue = null)

    private val gameWindows =
        GameWindows(client, windowRegistry, windowSettingsRepository, windowSettings, viewModelScope)

    /** Every open non-main window, in restore/open order; see [GameWindows]. */
    val windowUiStates: StateFlow<List<WindowUiState>> = gameWindows.windowUiStates

    val mainWindowUiState: StateFlow<WindowUiState> = gameWindows.mainWindowUiState

    private val _selectedWindow: MutableStateFlow<String> = MutableStateFlow(MAIN_WINDOW_NAME)
    val selectedWindow: StateFlow<String> = _selectedWindow

    private val scripts = ScriptControl(client, scriptManager, viewModelScope)

    private val commands = CommandHandler(client, scriptManager, scripts, aliases, scriptCommandPrefix)

    private val launcher = ScriptLauncher(client, scriptManager, mudScriptStore, commands, viewModelScope, ioDispatcher)

    private val history =
        CommandHistory(
            entry = entryTextState,
            characterId = client.characterId,
            commandHistoryRepository = commandHistoryRepository,
            minCommandLength = clientSettingRepository.observeMinCommandLength(),
            historySize = clientSettingRepository.observeHistorySize(),
            scope = viewModelScope,
        )

    /** The reverse history search in progress, or null when the entry is not searching. */
    val historySearch: StateFlow<HistorySearchState?> = history.search

    private val finder = WindowFinder { name -> (gameWindows.streamWindowUiState(name).data as? StreamWindowData)?.stream }

    val windowFindController: WindowFindController = finder

    private val macroRunner =
        MacroRunner(
            client = client,
            macroRepository = macroRepository,
            variableRepository = variableRepository,
            entry = entryTextState,
            handler = this,
            history = history,
            commands = commands,
            scope = viewModelScope,
        )

    val macroError: StateFlow<String?> = macroRunner.error

    val disconnected = client.disconnected

    val canReconnect: Boolean = reconnectAction != null

    // True while a reconnect is in flight, so the UI can show a progress dialog/indicator. A reconnect
    // re-dials the existing host/port/key (no SGE login), but the connection can still take a moment -
    // for a hosted session the router may hold the dial through a cold boot - so the user needs feedback.
    private val _reconnecting = MutableStateFlow(false)
    val reconnecting: StateFlow<Boolean> = _reconnecting.asStateFlow()

    private var reconnectJob: Job? = null

    val menuData = client.menuData

    init {
        handleClientEvents()
        trackMaxTypeAhead()
    }

    /**
     * Suspends until the character's saved open windows have been loaded into [windowUiStates].
     * See [GameWindows.awaitRestored].
     */
    suspend fun awaitWindowsRestored() {
        gameWindows.awaitRestored()
    }

    // Subscribes before the constructor returns, on whatever thread builds this view model. The
    // client's eventFlow has no replay, so an event emitted before this collector is registered is
    // lost to it - the first compass, or a window the game opens on login. Started undispatched, the
    // collector is in place before the factory starts the client (GameViewModelFactory.connect).
    // flowOn would not do: it subscribes from a new coroutine, queued like launchIn.
    //
    // The collector then runs off the main thread, and only the events handled below go on to the UI thread.
    // Every main-window line is an event too, and emit suspends the parser once eventFlow's buffer is
    // full, so collecting all of it on Main paced the parser to the UI thread. On Windows, where Tao
    // presents frames on that thread and waits for VSync, a large burst took over four seconds to
    // show instead of under one. Each handled event is launched rather than handled in withContext,
    // so the parser never waits on Main either; Main runs them in the order they were launched.
    private fun handleClientEvents() {
        viewModelScope.launch(ioDispatcher, start = CoroutineStart.UNDISPATCHED) {
            client.eventFlow
                .filter {
                    it is ClientCompassEvent ||
                        it is ClientOpenUrlEvent ||
                        it is ClientOpenWindowEvent ||
                        it is ClientCloseWindowEvent ||
                        it is ClientWindowInfoEvent
                }.collect { event ->
                    viewModelScope.launch { handleClientEvent(event) }
                }
        }
    }

    private fun handleClientEvent(event: ClientEvent) {
        when (event) {
            is ClientCompassEvent -> _compassState.value = event.directions.toSet()
            is ClientOpenUrlEvent -> openUrl(event.url)
            is ClientOpenWindowEvent -> gameWindows.openFromGame(name = event.name)
            is ClientCloseWindowEvent -> gameWindows.closeFromGame(event.name)
            is ClientWindowInfoEvent -> gameWindows.onWindowInfo(event.info)
            else -> Unit
        }
    }

    private fun trackMaxTypeAhead() {
        client.characterId
            .transformLatest {
                if (it != null) {
                    emitAll(characterSettingsRepository.observe(it, MAX_TYPE_AHEAD_KEY))
                }
            }.onEach { maxTypeAhead ->
                client.setMaxTypeAhead(maxTypeAhead?.toIntOrNull() ?: DEFAULT_MAX_TYPE_AHEAD)
            }.launchIn(viewModelScope)
    }

    override fun submit() {
        if (history.searching) {
            // Accept the current match: leave search mode and run it (if anything matched).
            history.acceptSearch()?.let { sendCommand(it) }
            return
        }
        val line = entryTextState.text.toString()
        if (line.isEmpty()) {
            // Pressing Enter on an empty entry should do nothing, not send a blank command.
            return
        }
        entryTextState.clearText()
        history.record(line)
        sendCommand(line)
    }

    fun sendCommand(command: String) {
        viewModelScope.launch {
            commands.handle(command)
        }
    }

    fun sendWidgetCommand(
        command: String,
        echo: String?,
    ) {
        viewModelScope.launch {
            client.sendWidgetCommand(command, echo)
        }
    }

    fun requestMenu(
        exist: String,
        noun: String?,
    ): Int = client.requestMenu(exist, noun)

    fun sendCommand(command: suspend () -> String) {
        viewModelScope.launch {
            commands.handle(command())
        }
    }

    override suspend fun stopScripts() {
        scripts.stopAll()
    }

    /** Tell the user which scripts the reconnect that opened this game stopped on the old one. */
    suspend fun reportScriptsStoppedByReconnect(names: List<String>) {
        scripts.reportStoppedByReconnect(names)
    }

    override suspend fun pauseScripts() {
        scripts.togglePauseAll()
    }

    override suspend fun repeatCommand(index: Int) {
        history.commandAt(index)?.let { commands.handle(it) }
    }

    fun runScript(file: Path) {
        launcher.runFile(file)
    }

    /** Run a leaf action button's inline WSL script. No-op for a group (which has no script). */
    fun runActionScript(action: Action) {
        launcher.runAction(action)
    }

    fun handleKeyPress(event: KeyEvent): Boolean = macroRunner.handleKeyPress(event)

    // Must be called from main thread
    private fun entryDelete(
        min: Int,
        max: Int,
    ) {
        entryTextState.edit {
            delete(min, max)
        }
    }

    fun entryInsert(text: String) {
        entryTextState.insertReplacingSelection(text)
    }

    override fun historyPrev() {
        history.previous()
    }

    override fun historyNext() {
        history.next()
    }

    override fun historySearch() {
        history.searchBack()
    }

    override fun historySearchExit() {
        history.exitSearch()
    }

    override fun findNext() {
        finder.nextOrOpen(selectedWindow.value)
    }

    override fun findPrev() {
        finder.previousOrOpen(selectedWindow.value)
    }

    /**
     * The selections of the open windows, so [copySelection] and [selectAll] can reach the one the
     * user is looking at. Populated by the window views; see [WindowSelectionController].
     */
    val windowSelectionController = WindowSelectionController()

    /**
     * Puts [text] on the platform clipboard, reporting whether it got there. The clipboard comes
     * from a window's composition, so there may not be one yet.
     */
    private suspend fun copyToClipboard(text: String): Boolean {
        val entry = clipEntryOf(text) ?: return false
        val clipboard = windowSelectionController.clipboard ?: return false
        // A SafeClipboard has swallowed a failed write by the time setClipEntry returns, so ask it
        // for the outcome rather than assume one: {cut} deletes what this copies.
        if (clipboard is SafeClipboard) return clipboard.trySetClipEntry(entry)
        clipboard.setClipEntry(entry)
        return true
    }

    override suspend fun copySelection() {
        // The entry comes first while it holds focus. These macros run in onPreviewKeyEvent and
        // consume the key, so nothing else will copy from the entry - and forwarding to the window
        // would copy nothing anyway: a SelectionContainer releases its selection the moment focus
        // leaves it, which is what taking focus into the entry does. The two are never both live.
        if (entryFocused.value) {
            val selection = entryTextState.selection
            if (!selection.collapsed) {
                copyToClipboard(entryTextState.text.substring(selection.min, selection.max))
                return
            }
        }
        val texts = windowSelectionController.selectionFor(selectedWindow.value)?.selectedTexts.orEmpty()
        if (texts.isEmpty()) return
        // One entry per selected text composable, which for a stream is one per line. Compose's own
        // copy puts a newline between them, so this is what ctrl-c in the window already produces.
        copyToClipboard(texts.joinToString("\n"))
    }

    override fun selectAll() {
        // Focus decides, as it does for copy: ctrl-a in a focused text field selects that field
        // everywhere else, and the macro consuming the key is the only thing left to do it here.
        if (entryFocused.value) {
            entryTextState.edit { selectAll() }
            return
        }
        // Only what is composed: a lazy list's off-screen lines are not selectable, which the
        // Compose API documents and we cannot work around from here.
        windowSelectionController.selectionFor(selectedWindow.value)?.selectAll()
    }

    private val entryFocused = MutableStateFlow(false)

    /** Reported by the entry composables, so {cut} can tell whether the entry is the user's target. */
    fun setEntryFocused(focused: Boolean) {
        entryFocused.value = focused
    }

    override suspend fun cutEntrySelection() {
        // Unlike copy, this one never leaves the entry. Cutting from a window would mean deleting
        // game text, and a cut aimed at an unfocused entry should do nothing rather than quietly
        // remove whatever happened to be selected there.
        if (!entryFocused.value) return
        val selection = entryTextState.selection
        if (selection.collapsed) return
        val cut = entryTextState.text.substring(selection.min, selection.max)
        // Nothing is deleted until the text is somewhere the user can get it back from.
        if (!copyToClipboard(cut)) return
        entryTextState.edit { replace(selection.min, selection.max, "") }
    }

    override suspend fun pasteIntoEntry() {
        val text =
            windowSelectionController.clipboard
                ?.getClipEntry()
                ?.plainText()
                ?.takeIf { it.isNotEmpty() }
                ?: return
        // Replaces the selection when there is one, inserts at the caret when there is not, and
        // leaves the caret after the text either way - so pasting twice gives you both.
        entryInsert(text)
    }

    /** The user opening a window; see [GameWindows.open]. */
    fun openWindow(name: String) {
        gameWindows.open(name)
    }

    /** The user closing a window, which is then remembered as hidden; see [GameWindows.close]. */
    fun closeWindow(name: String) {
        gameWindows.close(name)
    }

    fun clearStream(name: String) {
        viewModelScope.launch {
            val stream = windowRegistry.getOrCreateStream(name)
            stream.clear()
        }
    }

    /**
     * A [WindowUiState] for any window by [name], so the mobile tab layouts can render a stream
     * without opening it into a dock; see [GameWindows.streamWindowUiState].
     */
    fun streamWindowUiState(name: String): WindowUiState = gameWindows.streamWindowUiState(name)

    /** Shared handling for a clickable game-text action (command link or menu). */
    fun onWindowAction(action: WarlockAction): Int? =
        when (action) {
            is WarlockAction.SendCommand -> {
                sendCommand(action.command)
                null
            }

            is WarlockAction.SendCommandWithLookup -> {
                sendCommand(action.command)
                null
            }

            is WarlockAction.OpenMenu -> {
                action.onClick()
            }

            is WarlockAction.SendWidgetCommand -> {
                sendWidgetCommand(action.command, action.echo)
                null
            }

            is WarlockAction.RequestMenu -> {
                requestMenu(action.exist, action.noun)
            }

            is WarlockAction.ClosePanel -> {
                gameWindows.closeFromPanelButton(action)
                null
            }

            else -> {
                null
            }
        }

    fun saveWindowStyle(
        name: String,
        style: StyleDefinition,
    ) {
        viewModelScope.launch {
            client.characterId.value?.let { characterId ->
                windowSettingsRepository.setStyle(characterId = characterId, name = name, style = style)
            }
        }
    }

    /** Sets the per-window normal-font override (null clears it, falling back to the character default). */
    fun saveWindowFont(
        name: String,
        font: FontConfig?,
    ) {
        viewModelScope.launch {
            client.characterId.value?.let { characterId ->
                windowSettingsRepository.setFont(characterId = characterId, name = name, font = font)
            }
        }
    }

    /** Sets the per-window monospace-font override (null clears it, falling back to the character mono font). */
    fun saveWindowMonoFont(
        name: String,
        monoFont: FontConfig?,
    ) {
        viewModelScope.launch {
            client.characterId.value?.let { characterId ->
                windowSettingsRepository.setMonoFont(characterId = characterId, name = name, monoFont = monoFont)
            }
        }
    }

    fun saveWindowNameFilter(
        name: String,
        nameFilter: Boolean,
    ) {
        viewModelScope.launch {
            client.characterId.value?.let { characterId ->
                windowSettingsRepository.setNameFilter(characterId = characterId, name = name, nameFilter = nameFilter)
            }
        }
    }

    fun saveProgressBarColors(
        id: String,
        barColor: WarlockColor,
        backgroundColor: WarlockColor,
        textColor: WarlockColor,
    ) {
        viewModelScope.launch {
            client.characterId.value?.let { characterId ->
                progressBarSettingRepository.setColors(
                    characterId = characterId,
                    id = id,
                    barColor = barColor,
                    backgroundColor = backgroundColor,
                    textColor = textColor,
                )
            }
        }
    }

    fun saveProgressBarFont(
        id: String,
        fontFamily: String?,
        fontSize: Float?,
        fontWeight: Int?,
    ) {
        viewModelScope.launch {
            client.characterId.value?.let { characterId ->
                progressBarSettingRepository.setFont(
                    characterId = characterId,
                    id = id,
                    fontFamily = fontFamily,
                    fontSize = fontSize,
                    fontWeight = fontWeight,
                )
            }
        }
    }

    fun selectWindow(window: String) {
        _selectedWindow.value = window
    }

    override fun scroll(event: ScrollEvent) {
        _scrollEvents.update { it.adding(event) }
    }

    fun handledScrollEvent(event: ScrollEvent) {
        _scrollEvents.update { oldList ->
            oldList.removing(event)
        }
    }

    suspend fun close() {
        // Write any arrangement the bridge's debounce has not saved yet. This runs here rather than
        // from the composition because callers await close() before tearing the window down (and,
        // on the last window, before exiting the process), so the write is ordered ahead of both.
        dockLayout.flush()
        phoneTabs.flush()
        // Likewise the command history, whose save may have been launched but not yet run.
        history.flush()
        // If a reconnect is still in flight (e.g. the user returned to the dashboard while it was
        // running), cancel it first so the in-progress attempt is unwound: cancellation runs the
        // connect use case's finally blocks, which close any half-opened client/socket so nothing leaks.
        reconnectJob?.let { job ->
            reconnectJob = null
            job.cancelAndJoin()
        }
        closeClient()
        // No ViewModelStore owns this view model (it lives in GameState), so nothing will clear it
        // for us: without this, every collector it started would outlive the game.
        viewModelScope.cancel()
    }

    /**
     * Drop a game that never made it on screen: nothing was shown, saved or started by the user, so
     * there is no layout to flush and no one to send a quit for.
     */
    internal fun discard() {
        viewModelScope.cancel()
        client.close()
        windowRegistry.close()
    }

    /** Tear down this game's client and window registry without touching any reconnect in flight. */
    private suspend fun closeClient() {
        // Scripts run on the app's scope, not this view model's, so they would carry on against a
        // closed client - a waitfor that can never match, or a loop sending into nothing.
        scripts.stopForTeardown()
        // The quit is a goodbye to the server, not part of the teardown: whether it lands or not,
        // the client and the registry still have to go, and the caller still has somewhere to be
        // afterwards - the dashboard, or a window that wants to close. Only cancellation carries on
        // past here, and the teardown has run by then either way.
        try {
            if (!client.disconnected.value) {
                client.sendCommandDirect("quit")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.d(e) { "Couldn't send the parting quit" }
        } finally {
            client.close()
            windowRegistry.close()
        }
    }

    /**
     * Reconnect using the same credentials. This spins up a fresh client, window registry, and
     * GameViewModel (replacing this screen), so all prior game state is cleared. The old client and
     * window registry are then released. No-op if reconnecting isn't supported for this session, or
     * if a reconnect is already running.
     */
    fun reconnect() {
        val action = reconnectAction ?: return
        if (_reconnecting.value) return
        _reconnecting.value = true
        reconnectJob =
            viewModelScope.launch {
                try {
                    action()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // The connect paths report their own failures as an error screen, so getting here
                    // means nothing replaced this one: keep it, and its client, so the user can retry.
                    logger.e(e) { "Reconnect failed" }
                    return@launch
                } finally {
                    _reconnecting.value = false
                }
                // The reconnect installed a fresh game screen (or an error); release this (old)
                // client, and stop this view model's work, since nothing shows it any more. The
                // client's scripts go with it, which the user should hear about on the new screen.
                val stoppedScripts = scripts.runningNames()
                try {
                    closeClient()
                    if (stoppedScripts.isNotEmpty()) onReconnectStoppedScripts(stoppedScripts)
                } finally {
                    viewModelScope.cancel()
                }
            }
    }

    fun getCurrentTime(): Instant = client.getCurrentTime()

    fun saveEntryStyle(style: StyleDefinition) {
        viewModelScope.launch {
            client.characterId.value?.let { characterId ->
                presetRepository.save(characterId = characterId, key = "entry", style = style)
            }
        }
    }

    fun handledMacroError() {
        macroRunner.errorShown()
    }

    override fun entryClearToEnd() {
        entryDelete(entryTextState.selection.end, entryTextState.text.length)
    }

    override fun entryClearToStart() {
        entryDelete(0, entryTextState.selection.start)
    }

    override fun entryDeleteLastWord() {
        val index = entryText.substring(0, entryTextState.selection.start).trim().lastIndexOfAny(listOf(" ", "\t")) + 1
        if (index < entryText.length) {
            entryDelete(index, entryTextState.selection.start)
        }
    }

    override fun entrySetCursorPosition(pos: Int) {
        entryTextState.edit {
            selection = TextRange(pos)
        }
    }
}
