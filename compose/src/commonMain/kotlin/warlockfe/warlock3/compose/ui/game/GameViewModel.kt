package warlockfe.warlock3.compose.ui.game

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.delete
import androidx.compose.foundation.text.input.insert
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextRange
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
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
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.files.Path
import warlockfe.warlock3.compose.ui.window.ComposePanelState
import warlockfe.warlock3.compose.ui.window.ComposeTextStream
import warlockfe.warlock3.compose.ui.window.PanelWindowData
import warlockfe.warlock3.compose.ui.window.StreamTextLine
import warlockfe.warlock3.compose.ui.window.StreamWindowData
import warlockfe.warlock3.compose.ui.window.WindowData
import warlockfe.warlock3.compose.ui.window.WindowFindController
import warlockfe.warlock3.compose.ui.window.WindowFindUiState
import warlockfe.warlock3.compose.ui.window.WindowUiState
import warlockfe.warlock3.compose.ui.window.getStyle
import warlockfe.warlock3.compose.util.SAFE_DEFAULT_STYLE
import warlockfe.warlock3.compose.util.openUrl
import warlockfe.warlock3.core.client.ClientCloseWindowEvent
import warlockfe.warlock3.core.client.ClientCompassEvent
import warlockfe.warlock3.core.client.ClientOpenUrlEvent
import warlockfe.warlock3.core.client.ClientOpenWindowEvent
import warlockfe.warlock3.core.client.ClientWindowInfoEvent
import warlockfe.warlock3.core.client.GameCharacter
import warlockfe.warlock3.core.client.SendCommandType
import warlockfe.warlock3.core.client.WarlockAction
import warlockfe.warlock3.core.client.WarlockClient
import warlockfe.warlock3.core.compass.Direction
import warlockfe.warlock3.core.macro.MacroCommands
import warlockfe.warlock3.core.macro.MacroHandler
import warlockfe.warlock3.core.macro.MacroKeyCombo
import warlockfe.warlock3.core.macro.MacroToken
import warlockfe.warlock3.core.macro.ScrollEvent
import warlockfe.warlock3.core.macro.parseMacro
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
import warlockfe.warlock3.core.script.ScriptManager
import warlockfe.warlock3.core.script.ScriptStatus
import warlockfe.warlock3.core.text.Alias
import warlockfe.warlock3.core.text.FontConfig
import warlockfe.warlock3.core.text.StyleDefinition
import warlockfe.warlock3.core.text.StyledString
import warlockfe.warlock3.core.text.WarlockColor
import warlockfe.warlock3.core.text.WarlockStyle
import warlockfe.warlock3.core.util.splitFirstWord
import warlockfe.warlock3.core.window.WindowLocation
import warlockfe.warlock3.core.window.WindowMemoryUsage
import warlockfe.warlock3.core.window.WindowPlacement
import warlockfe.warlock3.core.window.WindowRegistry
import warlockfe.warlock3.core.window.WindowType
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

const val CLIENT_COMMAND_PREFIX = '/'

// Per-character setting key for the tablet layout's secondary (non-main) tabbed pane location.
const val TABLET_WINDOW_LOCATION_KEY = "tabletWindowLocation"

// The compose-docking layout JSON for this character, one blob per character. Written debounced on
// every layout change; the legacy per-window location/position rows only seed windows that are not
// in it yet (first run, or a window the character has never opened).
const val DOCK_LAYOUT_KEY = "dockLayout"

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
    private val commandHistoryRepository: CommandHistoryRepository,
    private val connectionRepository: ConnectionRepository,
    private val ioDispatcher: CoroutineDispatcher,
    private val reconnectAction: (suspend () -> Unit)? = null,
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

    val vitalBars: ComposePanelState = windowRegistry.getOrCreatePanel("minivitals") as ComposePanelState

    val indicators = client.indicators
    val leftHand = client.leftHand
    val rightHand = client.rightHand
    val spellHand = client.spellHand

    private val _macroError = MutableStateFlow<String?>(null)
    val macroError = _macroError.asStateFlow()

    // Find-in-window ("Ctrl+F") state. Non-null while the find overlay is open. findMatches is ordered
    // newest-first (the bottom-most occurrence is index 0), so stepping "next" moves further up the
    // buffer. findWindowName is the window captured when find opened; the overlay renders only there.
    private val findStateFlow = MutableStateFlow<WindowFindUiState?>(null)
    private val findFocusedFlow = MutableStateFlow(false)
    private var findMatches: List<FindMatch> = emptyList()
    private var findIndex = 0
    private var findQueryText = ""
    private var findWindowName = "main"

    val windowFindController: WindowFindController =
        object : WindowFindController {
            override val state: StateFlow<WindowFindUiState?> = findStateFlow.asStateFlow()

            override val focused: StateFlow<Boolean> = findFocusedFlow.asStateFlow()

            override fun setQuery(query: String) = updateFindQuery(query)

            override fun next() = findStep(1)

            override fun previous() = findStep(-1)

            override fun close() = closeFind()

            override fun setFocused(focused: Boolean) {
                findFocusedFlow.value = focused
            }
        }

    // Saved by macros
    private var storedText: String = ""

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
        observePerCharacter { characterId ->
            connectionRepository.observeWindowTitle(characterId)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = null,
        )

    val windowSettings =
        observePerCharacter { characterId ->
            windowSettingsRepository.observeWindowSettings(characterId)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = emptyList(),
        )

    val progressBarSettings =
        observePerCharacter { characterId ->
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
        observePerCharacter { characterId ->
            characterSettingsRepository
                .observe(characterId = characterId, key = SCRIPT_COMMAND_PREFIX_KEY)
                .map { it ?: "." }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = ".",
        )

    val topHeight = observeCharacterInt("topHeight")

    val bottomHeight = observeCharacterInt("bottomHeight")

    val leftWidth = observeCharacterInt("leftWidth")

    val rightWidth = observeCharacterInt("rightWidth")

    private val macros =
        client.characterId
            .flatMapLatest { characterId ->
                if (characterId != null) {
                    macroRepository.observeCharacterMacros(characterId)
                } else {
                    macroRepository.observeGlobalMacros()
                }.map { macroCommands ->
                    macroCommands.associate { it.keyCombo to it.action }
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = emptyMap(),
            )

    private val aliases: StateFlow<List<Alias>> =
        observePerCharacter { characterId ->
            aliasRepository.observeForCharacter(characterId)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList(),
        )

    // The configured action buttons for the current character (merged with global): the resolved
    // toolbar to draw plus the full pool so a group's children can be looked up by id.
    val actionBar: StateFlow<ActionBar> =
        observePerCharacter { characterId ->
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

    // The skin's named-color palette, so a window's skin-referenced text/background color resolves
    // (see WindowSettings.getStyle) instead of staying stuck at its last-saved literal.
    private val colorPalette = windowRegistry.colorPalette

    /** The character's default (normal) font; used as the base text style for windows without an override. */
    val defaultFont: StateFlow<FontConfig?> =
        observePerCharacter { characterSettingsRepository.observeDefaultFont(it) }
            .stateIn(scope = viewModelScope, started = SharingStarted.Eagerly, initialValue = null)

    /** The character's monospace font; used for monospace-flagged text without a per-window override. */
    val monoFont: StateFlow<FontConfig?> =
        observePerCharacter { characterSettingsRepository.observeMonoFont(it) }
            .stateIn(scope = viewModelScope, started = SharingStarted.Eagerly, initialValue = null)

    private val runningScripts =
        scriptManager.runningScripts.stateIn(viewModelScope, SharingStarted.Eagerly, persistentMapOf())

    /** What this connection's windows are retaining, for the memory usage view. */
    suspend fun memoryUsage(): WindowMemoryUsage = windowRegistry.memoryUsage()

    val runningScriptCount: Int get() = runningScripts.value.size

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

    private var historyPosition = 0
    private val sendHistory = mutableListOf("")

    // Reverse history search (readline-style ctrl+r, via the {HistorySearch} macro). Non-null while
    // the entry is in "searching" mode; it holds the live query (which mirrors the entry text the
    // user types) and the history command currently matched. historySearchIndex selects which match,
    // counting back from the most recent (0). The entry prompt renders `searching "<query>": <match>`
    // while this is set.
    private val _historySearch = MutableStateFlow<HistorySearchState?>(null)
    val historySearch: StateFlow<HistorySearchState?> = _historySearch.asStateFlow()
    private var historySearchIndex = 0

    // Every open non-main window with the dock its saved settings remember, in restore/open order
    // (windows sharing a dock stay in their saved order). The docking bridge reconciles the dock
    // tree from this; the location only seeds a window's default spot there.
    private val _windowUiStates = MutableStateFlow<List<OpenGameWindow>>(emptyList())
    val windowUiStates: StateFlow<List<OpenGameWindow>> = _windowUiStates.asStateFlow()

    // Completed once the saved window layout has been restored into the dock lists (also on a
    // failed restore - see restoreWindowLayoutOnConnect). A Deferred rather than a resettable
    // flag: the dock lists are never cleared, so "restored" can never become false again.
    // Game-announced opens and closes wait on it so they land after the saved layout instead of
    // racing it into the docks.
    private val layoutRestored = CompletableDeferred<Unit>()

    // Stamp of the game's latest open/close instruction per window, taken while the event
    // collector is still synchronous. The handlers suspend (on the character id and the layout
    // restore) before acting, so each re-checks that it still holds the newest instruction and
    // drops itself otherwise - without this, an open parked on the restore could land after the
    // close that superseded it.
    private var gameWindowEventCounter = 0L
    private val latestGameWindowEvent = mutableMapOf<String, Long>()

    private fun stampGameWindowEvent(name: String): Long {
        val stamp = ++gameWindowEventCounter
        latestGameWindowEvent[name] = stamp
        return stamp
    }

    // On-demand window ui states for the mobile phone/tablet stream tabs, keyed by window name, so
    // switching tabs reuses the same stream and scroll state instead of rebuilding each time.
    private val tabWindowUiStates = mutableMapOf<String, WindowUiState>()

    private val _mainWindowUiState =
        MutableStateFlow(
            WindowUiState(
                name = "main",
                windowInfo = mutableStateOf(windows.value.firstOrNull { it.name == "main" }),
                style = SAFE_DEFAULT_STYLE,
                data =
                    StreamWindowData(
                        stream = windowRegistry.getOrCreateStream("main") as ComposeTextStream,
                    ),
                width = null,
                height = null,
            ),
        )
    val mainWindowUiState: StateFlow<WindowUiState> = _mainWindowUiState.asStateFlow()

    private val _selectedWindow: MutableStateFlow<String> = MutableStateFlow("main")
    val selectedWindow: StateFlow<String> = _selectedWindow

    // Commands shorter than this are not saved to history; history is capped at historySize entries.
    // Both are seeded from client settings (see init); the defaults apply until the flows emit.
    private var minHistoryLen = ClientSettingRepository.DEFAULT_MIN_COMMAND_LENGTH
    private var historySize = ClientSettingRepository.DEFAULT_HISTORY_SIZE

    val disconnected = client.disconnected

    val canReconnect: Boolean = reconnectAction != null

    // True while a reconnect is in flight, so the UI can show a progress dialog/indicator. A reconnect
    // re-dials the existing host/port/key (no SGE login), but the connection can still take a moment -
    // for a hosted session the router may hold the dial through a cold boot - so the user needs feedback.
    private val _reconnecting = MutableStateFlow(false)
    val reconnecting: StateFlow<Boolean> = _reconnecting.asStateFlow()

    private var reconnectJob: Job? = null

    val menuData = client.menuData

    /**
     * Observe a per-character flow, switching whenever the connected character changes and emitting
     * nothing until a character is connected. Backs the many per-character settings flows above.
     */
    private fun <T> observePerCharacter(block: (characterId: String) -> Flow<T>): Flow<T> =
        client.characterId.flatMapLatest { characterId ->
            if (characterId != null) block(characterId) else flow {}
        }

    /** Observe a per-character integer setting stored as a string, falling back to [default]. */
    private fun observeCharacterInt(
        key: String,
        default: Int = 200,
    ): Flow<Int> =
        observePerCharacter { characterId ->
            characterSettingsRepository.observe(characterId = characterId, key = key).map { it?.toIntOrNull() ?: default }
        }

    /** Build the [WindowData] for a window of the given [windowType], or null if it carries none. */
    private fun createWindowData(
        windowType: WindowType?,
        name: String,
    ): WindowData? =
        when (windowType) {
            WindowType.STREAM -> StreamWindowData(windowRegistry.getOrCreateStream(name) as ComposeTextStream)
            WindowType.PANEL -> PanelWindowData(windowRegistry.getOrCreatePanel(name) as ComposePanelState)
            else -> null
        }

    init {
        trackMinCommandLength()
        trackHistorySize()
        loadCommandHistoryOnConnect()
        restoreWindowLayoutOnConnect()
        applyWindowSettingsChanges()
        handleClientEvents()
        trackMaxTypeAhead()
        publishRunningScripts()
        trackHistorySearchQuery()
    }

    private fun trackHistorySearchQuery() {
        viewModelScope.launch {
            // While searching, the entry text is the live query; whenever it changes, re-run the
            // search from the most recent match. Ignored when not in search mode.
            snapshotFlow { entryTextState.text.toString() }
                .collect {
                    if (_historySearch.value != null) {
                        updateHistorySearch(resetIndex = true)
                    }
                }
        }
    }

    private fun trackMinCommandLength() {
        clientSettingRepository
            .observeMinCommandLength()
            .onEach { minHistoryLen = it }
            .launchIn(viewModelScope)
    }

    private fun trackHistorySize() {
        clientSettingRepository
            .observeHistorySize()
            .onEach {
                historySize = it
                trimHistory()
            }.launchIn(viewModelScope)
    }

    // Load each character's saved command history when it connects.
    private fun loadCommandHistoryOnConnect() {
        client.characterId
            .filterNotNull()
            .distinctUntilChanged()
            .onEach { characterId ->
                val saved = commandHistoryRepository.load(characterId)
                // sendHistory[0] is the in-progress entry buffer; commands follow it newest-first,
                // while the file stores them oldest-first, so reverse on load.
                sendHistory.clear()
                sendHistory.add("")
                saved.asReversed().forEach { sendHistory.add(it) }
                trimHistory()
                historyPosition = 0
            }.launchIn(viewModelScope)
    }

    // Restore the saved window layout for a character when it connects.
    private fun restoreWindowLayoutOnConnect() {
        client.characterId
            .onEach { characterId ->
                if (characterId != null) {
                    try {
                        try {
                            // Heal duplicate/gapped positions before reading: SQLite returns
                            // duplicates in an unspecified order, so until they are renumbered
                            // the restored order need not match the one the user last saw.
                            windowSettingsRepository.normalizePositions(characterId)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            // Restoring from the un-normalized read beats not restoring at all.
                            logger.w(e) { "Failed to normalize window positions" }
                        }
                        val settings = windowSettingsRepository.observeWindowSettings(characterId).first()
                        settings.filter { it.open }.forEach { entity ->
                            // A user open, or a game open that gave up waiting on this restore,
                            // can land first - never add a second copy.
                            if (_windowUiStates.value.any { it.name == entity.name }) {
                                return@forEach
                            }
                            logger.d { "Loading entity: $entity" }
                            val window = windows.value.firstOrNull { it.name == entity.name }
                            val uiState =
                                WindowUiState(
                                    name = entity.name,
                                    windowInfo = mutableStateOf(window),
                                    style = entity.getStyle(colorPalette.value),
                                    font = entity.font,
                                    monoFont = entity.monoFont,
                                    width = entity.width,
                                    height = entity.height,
                                    nameFilter = entity.nameFilter,
                                    data = createWindowData(window?.windowType, entity.name),
                                )
                            (uiState.data as? StreamWindowData)?.stream?.setNameFilter(entity.nameFilter)
                            when (val location = entity.location) {
                                WindowLocation.MAIN -> _mainWindowUiState.value = uiState

                                null -> Unit

                                // Never placed; nothing to restore
                                else -> _windowUiStates.update { it + OpenGameWindow(location, uiState) }
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // Swallowing keeps the collector alive for the next connection and off
                        // viewModelScope's unhandled path (a crash on Android).
                        logger.w(e) { "Failed to restore the window layout" }
                    } finally {
                        // Released even when the restore failed: parked opens append after the
                        // dock's saved positions (computed in the DB), so proceeding against a
                        // half-populated layout cannot corrupt it, while holding the gate would
                        // stall every panel on its timeout.
                        layoutRestored.complete(Unit)
                    }
                }
            }.launchIn(viewModelScope)
    }

    // Re-apply window styling and name filters whenever the saved window settings change.
    private fun applyWindowSettingsChanges() {
        windowSettings
            .onEach { currentWindowSettings ->
                currentWindowSettings.forEach { singleWindowSettings ->
                    if (singleWindowSettings.name == "main") {
                        _mainWindowUiState.update {
                            (it.data as? StreamWindowData)?.stream?.setNameFilter(singleWindowSettings.nameFilter)
                            it.copy(
                                style = singleWindowSettings.getStyle(colorPalette.value),
                                font = singleWindowSettings.font,
                                monoFont = singleWindowSettings.monoFont,
                                nameFilter = singleWindowSettings.nameFilter,
                            )
                        }
                    } else {
                        _windowUiStates.update { states ->
                            val index = states.indexOfFirst { it.name == singleWindowSettings.name }
                            if (index != -1) {
                                val mutableStates = states.toMutableList()
                                (states[index].uiState.data as? StreamWindowData)
                                    ?.stream
                                    ?.setNameFilter(singleWindowSettings.nameFilter)
                                mutableStates[index] =
                                    states[index].copy(
                                        uiState =
                                            states[index].uiState.copy(
                                                style = singleWindowSettings.getStyle(colorPalette.value),
                                                font = singleWindowSettings.font,
                                                monoFont = singleWindowSettings.monoFont,
                                                nameFilter = singleWindowSettings.nameFilter,
                                            ),
                                    )
                                mutableStates
                            } else {
                                states
                            }
                        }
                    }
                }
            }.launchIn(viewModelScope)
    }

    private fun handleClientEvents() {
        client.eventFlow
            .onEach { event ->
                when (event) {
                    is ClientCompassEvent -> {
                        _compassState.value = event.directions.toSet()
                    }

                    is ClientOpenUrlEvent -> {
                        openUrl(event.url)
                    }

                    is ClientOpenWindowEvent -> {
                        openWindowFromGame(name = event.name, protocolLocation = event.location)
                    }

                    is ClientCloseWindowEvent -> {
                        closeWindowFromGame(event.name)
                    }

                    is ClientWindowInfoEvent -> {
                        if (event.info.name == "main") {
                            _mainWindowUiState.value.windowInfo.value = event.info
                        } else {
                            // A non-resident panel persists nothing, but one announced late can
                            // have picked up a saved row before its info arrived (canSaveWindow
                            // is true for unknown windows). Clear it, or the restore and every
                            // reorder keep tracking a window that saves nothing.
                            if (!event.info.resident) {
                                viewModelScope.launch {
                                    client.characterId.value?.let { characterId ->
                                        windowSettingsRepository.removeWindowFromLayout(characterId, event.info.name)
                                    }
                                }
                            }
                            _windowUiStates.value
                                .indexOfFirst { it.name == event.info.name }
                                .takeIf { it != -1 }
                                ?.let { index ->
                                    val uiState = _windowUiStates.value[index].uiState
                                    uiState.windowInfo.value = event.info
                                    if (uiState.data == null) {
                                        _windowUiStates.update { states ->
                                            val mutableStates = states.toMutableList()
                                            val filled =
                                                uiState.copy(
                                                    data = createWindowData(event.info.windowType, event.info.name),
                                                )
                                            mutableStates[index] = states[index].copy(uiState = filled)
                                            (filled.data as? StreamWindowData)
                                                ?.stream
                                                ?.setNameFilter(uiState.nameFilter)
                                            mutableStates
                                        }
                                    }
                                }
                        }
                    }

                    else -> {
                        // don't care
                    }
                }
            }.launchIn(viewModelScope)
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

    // Render the running-scripts status lines (with pause/resume/stop links) into the scripts window.
    private fun publishRunningScripts() {
        runningScripts
            .onEach { scripts ->
                val scriptStream = client.getStream("warlockscripts")
                scriptStream.clear()
                scripts.forEach { entry ->
                    val instance = entry.value.instance
                    var text = StyledString("${instance.name}: ${instance.status} ")
                    when (instance.status) {
                        ScriptStatus.Running -> {
                            text +=
                                StyledString(
                                    "pause",
                                    WarlockStyle.Link(WarlockAction.SendCommand("/pause ${entry.key}")),
                                )
                        }

                        ScriptStatus.Suspended -> {
                            text +=
                                StyledString(
                                    "resume",
                                    WarlockStyle.Link(WarlockAction.SendCommand("/resume ${entry.key}")),
                                )
                        }

                        else -> {
                            // do nothing
                        }
                    }
                    text += StyledString(" ") +
                        StyledString("stop", WarlockStyle.Link(WarlockAction.SendCommand("/kill ${entry.key}")))
                    scriptStream.appendLine(text, false)
                }
            }.launchIn(viewModelScope)
    }

    override fun submit() {
        val search = _historySearch.value
        if (search != null) {
            // Accept the current match: leave search mode and run it (if anything matched).
            _historySearch.value = null
            historySearchIndex = 0
            val match = search.match
            entryTextState.clearText()
            if (match != null) {
                updateHistory(match)
                historyPosition = 0
                sendCommand(match)
            }
            return
        }
        val line = entryTextState.text.toString()
        if (line.isEmpty()) {
            // Pressing Enter on an empty entry should do nothing, not send a blank command.
            return
        }
        entryTextState.clearText()
        updateHistory(line)
        historyPosition = 0
        sendCommand(line)
    }

    private fun applyAliases(line: String): String = aliases.value.fold(line) { acc, alias -> alias.replace(acc) }

    private fun updateHistory(line: String) {
        if (line.length >= minHistoryLen && sendHistory.getOrNull(1) != line) {
            sendHistory[0] = line
            sendHistory.add(0, "")
            trimHistory()
            persistHistory()
        }
    }

    // Persist the current command history (commands only, oldest first) for the connected character.
    private fun persistHistory() {
        val characterId = client.characterId.value ?: return
        val commands = sendHistory.drop(1).filter { it.isNotEmpty() }.asReversed()
        viewModelScope.launch {
            commandHistoryRepository.save(characterId, commands)
        }
    }

    // sendHistory[0] is the in-progress entry buffer; indices 1..n are the stored commands, so the
    // list holds at most historySize + 1 elements.
    private fun trimHistory() {
        while (sendHistory.size > historySize + 1) {
            sendHistory.removeAt(sendHistory.size - 1)
        }
    }

    fun sendCommand(command: String) {
        viewModelScope.launch {
            commandHandler(command)
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
            commandHandler(command())
        }
    }

    override suspend fun stopScripts() {
        val scripts = scriptManager.runningScripts.value.values
        val count = scripts.size
        if (count > 0) {
            scripts.forEach { script ->
                script.instance.stop()
            }
            client.print(StyledString("Stopped $count script(s)"))
        }
    }

    override suspend fun pauseScripts() {
        val scriptInstances =
            scriptManager.runningScripts.value.values
                .map { it.instance }
        if (scriptInstances.isNotEmpty()) {
            val paused = !scriptInstances.any { it.status == ScriptStatus.Running }
            if (paused) {
                client.print(StyledString("Resumed script(s)"))
            } else {
                client.print(StyledString("Paused script(s)"))
            }
            for (instance in scriptInstances) {
                if (paused) {
                    instance.resume()
                } else {
                    instance.suspend()
                }
            }
        } else {
            client.print(StyledString("No scripts running"))
        }
    }

    override suspend fun repeatCommand(index: Int) {
        val command = sendHistory.getOrNull(index)
        if (command != null) {
            commandHandler(command)
        }
    }

    fun runScript(file: Path) {
        viewModelScope.launch(ioDispatcher) {
            scriptManager.startScript(client, file, ::commandHandler)
        }
    }

    /** Run a leaf action button's inline WSL script. No-op for a group (which has no script). */
    fun runActionScript(action: Action) {
        val script = action.script ?: return
        viewModelScope.launch(ioDispatcher) {
            scriptManager.startScript(client, action.name, script, ::commandHandler)
        }
    }

    fun handleKeyPress(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown || event.key == Key.Unknown) {
            return false
        }

        val keyCombo = translateKeyPress(event)
        val macroString = macros.value[keyCombo]

        if (macroString != null) {
            val tokens = parseMacro(macroString)

            if (tokens == null) {
                viewModelScope.launch {
                    client.print(StyledString("Invalid macro: $macroString"))
                }
                return false
            }

            executeMacro(tokens)

            return true
        }

        if (event.key.keyCode == Key.Enter.keyCode) {
            submit()
            return true
        }

        return false
    }

    private fun executeMacro(tokens: List<MacroToken>) {
        viewModelScope.launch {
            var moveCursor: Int? = null
            tokens.forEach { token ->
                when (token) {
                    is MacroToken.Entity -> {
                        handleEntity(
                            entity = token.char,
                            onEntryCleared = {
                                // TODO: report this as an error
                                moveCursor = null
                            },
                        )
                    }

                    MacroToken.At -> {
                        moveCursor = entryTextState.selection.min
                    }

                    is MacroToken.Text -> {
                        entryInsert(token.text)
                    }

                    is MacroToken.Variable -> {
                        client.characterId.value?.lowercase()?.let { characterId ->
                            variableRepository.getVariable(characterId, token.name)?.let { value ->
                                entryInsert(value)
                            }
                        }
                    }

                    is MacroToken.Command -> {
                        if (!MacroCommands.execute(token.name, this@GameViewModel)) {
                            _macroError.value = "Macro command not found: ${token.name}"
                        }
                    }
                }
            }
            if (moveCursor != null) {
                entryTextState.edit {
                    selection = TextRange(moveCursor!!)
                }
            }
        }
    }

    private suspend fun handleEntity(
        entity: Char,
        onEntryCleared: () -> Unit,
    ) {
        when (entity) {
            'x' -> {
                storedText = entryTextState.text.toString()
                entryTextState.clearText()
                onEntryCleared()
            }

            'r' -> {
                val line = entryTextState.text.toString()
                entryTextState.clearText()
                updateHistory(line)
                historyPosition = 0
                val aliasedLine = applyAliases(line)
                commandHandler(aliasedLine)
                onEntryCleared()
            }

            'p' -> {
                delay(1.seconds)
            }

            '?' -> {
                entryTextState.edit {
                    append(storedText)
                }
            }
        }
    }

    // Must be called from main thread
    private fun entryDelete(
        min: Int,
        max: Int,
    ) {
        entryTextState.edit {
            delete(min, max)
        }
    }

    // Must be called from main thread
    private fun entrySetSelection(selection: TextRange) {
        entryTextState.edit {
            this.selection = selection
        }
    }

    fun entryInsert(text: String) {
        entryTextState.edit {
            if (selection.length > 0) {
                delete(selection.min, selection.max)
            }
            insert(selection.min, text)
        }
    }

    override fun historyPrev() {
        val history = sendHistory
        if (historyPosition < history.size - 1) {
            sendHistory[historyPosition] = entryTextState.text.toString()
            historyPosition++
            entryTextState.setTextAndPlaceCursorAtEnd(history[historyPosition])
        }
    }

    override fun historyNext() {
        if (historyPosition > 0) {
            sendHistory[historyPosition] = entryTextState.text.toString()
            historyPosition--
            entryTextState.setTextAndPlaceCursorAtEnd(sendHistory[historyPosition])
        }
    }

    override fun historySearch() {
        if (_historySearch.value == null) {
            // Enter search mode with a fresh, empty query (the entry text becomes the query).
            historySearchIndex = 0
            historyPosition = 0
            entryTextState.clearText()
            updateHistorySearch(resetIndex = true)
        } else {
            // Already searching: step further back to the next older match.
            historySearchIndex++
            updateHistorySearch(resetIndex = false)
        }
    }

    override fun historySearchExit() {
        val current = _historySearch.value ?: return
        // Leave search mode, keeping the matched command in the entry for editing or sending.
        _historySearch.value = null
        historySearchIndex = 0
        entryTextState.setTextAndPlaceCursorAtEnd(current.match ?: "")
    }

    // Recompute the current match for the active history search from the live query (the entry text).
    // A blank query matches nothing; a blank or otherwise non-matching query keeps the previously
    // matched entry selected rather than clearing it. sendHistory is newest-first, with index 0
    // reserved for the in-progress buffer.
    private fun updateHistorySearch(resetIndex: Boolean) {
        val query = entryTextState.text.toString()
        val matches =
            if (query.isBlank()) {
                emptyList()
            } else {
                sendHistory.drop(1).filter { it.isNotEmpty() && it.contains(query, ignoreCase = true) }
            }
        val match =
            if (matches.isEmpty()) {
                // Keep the previously matched entry selected.
                _historySearch.value?.match
            } else {
                if (resetIndex) {
                    historySearchIndex = 0
                }
                historySearchIndex = historySearchIndex.coerceIn(0, matches.size - 1)
                matches[historySearchIndex]
            }
        _historySearch.value =
            HistorySearchState(
                query = query,
                match = match,
            )
    }

    override fun findNext() {
        if (findStateFlow.value == null) openFind() else findStep(1)
    }

    override fun findPrev() {
        if (findStateFlow.value == null) openFind() else findStep(-1)
    }

    // Open the find overlay over the currently selected window. No-op if that window has no text
    // stream (e.g. a panel window). Starts with an empty query, so nothing matches until the user
    // types.
    private fun openFind() {
        val target = selectedWindow.value
        if ((streamWindowUiState(target).data as? StreamWindowData)?.stream == null) return
        findWindowName = target
        findQueryText = ""
        findIndex = 0
        recomputeFindMatches()
    }

    private fun updateFindQuery(query: String) {
        if (findStateFlow.value == null) return
        findQueryText = query
        findIndex = 0
        recomputeFindMatches()
    }

    // Step to another match, wrapping around. +1 is "next" (further up/older), -1 is "previous".
    private fun findStep(delta: Int) {
        if (findStateFlow.value == null) return
        val size = findMatches.size
        if (size == 0) return
        findIndex = ((findIndex + delta) % size + size) % size
        publishFindState()
    }

    private fun closeFind() {
        findStateFlow.value = null
        findFocusedFlow.value = false
        findMatches = emptyList()
        findIndex = 0
        findQueryText = ""
    }

    // Re-scan the target window's lines for the current query, ordering matches newest-first so the
    // bottom-most occurrence is selected first.
    private fun recomputeFindMatches() {
        val stream = (streamWindowUiState(findWindowName).data as? StreamWindowData)?.stream
        val docOrder = mutableListOf<FindMatch>()
        if (findQueryText.isNotEmpty() && stream != null) {
            stream.lines.value.forEach { line ->
                val text = (line as? StreamTextLine)?.text?.text
                if (text != null) {
                    var i = text.indexOf(findQueryText, ignoreCase = true)
                    while (i >= 0) {
                        docOrder.add(FindMatch(line.serialNumber, i until (i + findQueryText.length)))
                        i = text.indexOf(findQueryText, startIndex = i + findQueryText.length, ignoreCase = true)
                    }
                }
            }
        }
        findMatches = docOrder.asReversed()
        findIndex = findIndex.coerceIn(0, maxOf(0, findMatches.size - 1))
        publishFindState()
    }

    private fun publishFindState() {
        val current = findMatches.getOrNull(findIndex)
        findStateFlow.value =
            WindowFindUiState(
                windowName = findWindowName,
                query = findQueryText,
                totalMatches = findMatches.size,
                currentNumber = if (findMatches.isEmpty()) 0 else findIndex + 1,
                matchRangesBySerial =
                    findMatches
                        .groupBy { it.serialNumber }
                        .mapValues { (_, matches) -> matches.map { it.range }.sortedBy { it.first } },
                currentSerial = current?.serialNumber,
                currentRange = current?.range,
            )
    }

    // TODO: convert this into a simpler representation
    private fun translateKeyPress(event: KeyEvent): MacroKeyCombo =
        MacroKeyCombo(
            keyCode = event.key.keyCode,
            ctrl = event.isCtrlPressed,
            alt = event.isAltPressed,
            shift = event.isShiftPressed,
            meta = event.isMetaPressed,
        )

    fun setLocationSize(
        location: WindowLocation,
        size: Int,
    ) {
        client.characterId.value?.let { characterId ->
            viewModelScope.launch {
                val key =
                    when (location) {
                        WindowLocation.LEFT -> "leftWidth"
                        WindowLocation.RIGHT -> "rightWidth"
                        WindowLocation.TOP -> "topHeight"
                        WindowLocation.BOTTOM -> "bottomHeight"
                        WindowLocation.MAIN -> error("Cannot set size on main location")
                    }
                characterSettingsRepository.save(characterId, key, size.toString())
            }
        }
    }

    fun openWindow(name: String) {
        if (client.characterId.value == null || layoutRestored.isCompleted) {
            openWindowNow(name)
        } else {
            // Clicked during login: wait for the saved layout, or the remembered rank would map
            // onto a not-yet-populated dock and pin the window at the top.
            viewModelScope.launch {
                withTimeoutOrNull(10.seconds) { layoutRestored.await() }
                openWindowNow(name)
            }
        }
    }

    /**
     * Docks a window at the placement the observed settings remember (TOP for one never placed)
     * and then persists. The dock updates before anything suspends - normally in the same frame
     * as the click - so a close arriving afterwards always finds it docked, and the screen and
     * the saved state cannot disagree about whether it is open.
     */
    private fun openWindowNow(name: String) {
        val placement = rememberedPlacement(name)
        if (placement == null) {
            openWindowAt(name = name, location = WindowLocation.TOP)
            notifyPanelVisibility(name, open = true)
            return
        }
        if (!placeWindowUi(name = name, location = placement.location, position = placement.position)) return
        notifyPanelVisibility(name, open = true)
        viewModelScope.launch {
            client.characterId.value?.let { characterId ->
                // Recomputed against the DB rows inside the transaction; the observed placement
                // above only seeded the on-screen spot.
                windowSettingsRepository.reopenWindow(characterId, name)
            }
        }
    }

    /**
     * The placement a window's saved row remembers - its dock plus its rank among the dock's
     * open windows - read from the observed settings so callers can dock without a DB
     * round-trip; null when the window has never been placed. A remembered MAIN reads as never
     * placed, mirroring [WindowSettingsRepository.reopenWindow]: MAIN is the main text window's
     * slot.
     */
    private fun rememberedPlacement(name: String): WindowPlacement? {
        if (!canSaveWindow(name)) return null
        val settings = windowSettings.value
        val window = settings.firstOrNull { it.name == name } ?: return null
        val location = window.location?.takeUnless { it == WindowLocation.MAIN } ?: return null
        val rank =
            settings.count {
                it.open && it.location == location && it.name != name && compareValues(it.position, window.position) < 0
            }
        return WindowPlacement(location, rank)
    }

    /**
     * Tells the server when the user shows or hides a dialog panel, as Wrayth does, so it knows
     * whether sending that panel's updates is worthwhile.
     */
    private fun notifyPanelVisibility(
        name: String,
        open: Boolean,
    ) {
        if (windows.value.firstOrNull { it.name == name }?.windowType != WindowType.PANEL) return
        viewModelScope.launch {
            client.sendCommandDirect(if (open) "_DBOPEN $name" else "_DBCLOSE $name")
        }
    }

    /**
     * Opens a window because the game asked for it with an `openDialog` tag, at the location the
     * protocol named. A window the game names somewhere we do not dock panels stays registered but
     * closed, which is what the game's own client does with one.
     *
     * A window the character already has a saved dock for is left to the saved layout: the tag says
     * the panel exists, not where the user keeps it. That is read from the repository rather than the
     * observed settings because these tags arrive during login, possibly before the settings flow has
     * emitted, and guessing "unsaved" there would overwrite the user's placement.
     *
     * A window the user closed is left closed. The game announces its panels on every login, so
     * without that a panel the user does not want would come back every time they connect.
     */
    private fun openWindowFromGame(
        name: String,
        protocolLocation: WindowLocation?,
    ) {
        if (name == _mainWindowUiState.value.name) return
        // MAIN belongs to the main text window; a panel can never take that slot.
        val location = protocolLocation?.takeUnless { it == WindowLocation.MAIN } ?: return
        val stamp = stampGameWindowEvent(name)
        viewModelScope.launch {
            // The game announces most of its windows before naming the character (GS4 sends the
            // whole panel list ahead of the <app> tag), and the guards below are meaningless
            // without the character's saved state - sampling a still-null id here is what used to
            // reopen hidden panels and duplicate docked ones on every GS4 login. Wait for the id;
            // the timeout only covers a server that never identifies the character at all.
            val characterId =
                withTimeoutOrNull(10.seconds) { client.characterId.filterNotNull().first() }
            if (characterId != null) {
                // The layout restore wakes on this same characterId emission; let it finish so
                // this window lands after the saved layout instead of racing it into the dock.
                if (withTimeoutOrNull(10.seconds) { layoutRestored.await() } == null) {
                    logger.w { "Layout restore still running after 10s; opening $name against it" }
                }
                // A window the user closed stays closed until they ask for it back.
                if (windowSettingsRepository.isHidden(characterId, name)) return@launch
                if (windowSettingsRepository.getWindowLocation(characterId, name) != null) return@launch
            }
            // Superseded by a later open or close for this window while suspended above.
            if (latestGameWindowEvent[name] != stamp) return@launch
            // A window the game closed earlier reopens at the placement that close remembered;
            // the protocol's location is only the default for one never placed.
            val placement =
                if (characterId != null && canSaveWindow(name)) {
                    windowSettingsRepository.reopenWindow(characterId, name)
                } else {
                    null
                }
            if (placement != null) {
                placeWindowUi(name = name, location = placement.location, position = placement.position)
            } else {
                openWindowAt(name = name, location = location)
            }
        }
    }

    private fun openWindowAt(
        name: String,
        location: WindowLocation,
    ) {
        if (!placeWindowUi(name = name, location = location)) return
        if (!canSaveWindow(name)) return
        viewModelScope.launch {
            client.characterId.value?.let { characterId ->
                // The position is assigned in the DB - the end of this dock's saved positions -
                // so transient panels in the on-screen list and concurrent opens cannot skew it.
                windowSettingsRepository.openWindowAtEnd(
                    characterId = characterId,
                    name = name,
                    location = location,
                )
            }
        }
    }

    /**
     * Docks a window at [position], its rank among the dock's open saved windows (null appends),
     * and returns whether it was added. The rank maps to the on-screen index of the window
     * currently holding it: an on-screen window without an open saved row - a transient panel,
     * or one whose first save is still in flight - holds a screen slot but no rank. Persists
     * nothing: callers own the saved placement.
     */
    private fun placeWindowUi(
        name: String,
        location: WindowLocation,
        position: Int? = null,
    ): Boolean {
        // A window lives in the list exactly once: an open for one already there is a no-op
        // (the visibility toggles route those to closeWindow).
        if (_windowUiStates.value.any { it.name == name }) return false
        var newState: WindowUiState? = null
        _windowUiStates.update { states ->
            // Reset on entry: update{} may retry, and a retry can take the other branch.
            newState = null
            if (states.any { it.name == name }) {
                states
            } else {
                val added = buildWindowUiState(name)
                newState = added
                // The end of this dock's group: after its last window, or the end of the list
                // when it has none yet.
                val groupEnd =
                    states.indexOfLast { it.location == location }.let { last ->
                        if (last == -1) states.size else last + 1
                    }
                val insertIndex =
                    if (position == null) {
                        groupEnd
                    } else {
                        val openNames = windowSettings.value.filter { it.open }.mapTo(mutableSetOf()) { it.name }
                        states
                            .withIndex()
                            .filter { it.value.location == location && it.value.name in openNames }
                            .getOrNull(position)
                            ?.index
                            ?: groupEnd
                    }
                states.toMutableList().apply { add(insertIndex, OpenGameWindow(location, added)) }
            }
        }
        val state = newState ?: return false
        (state.data as? StreamWindowData)?.stream?.setNameFilter(state.nameFilter)
        return true
    }

    private fun buildWindowUiState(name: String): WindowUiState {
        val entity = windowSettings.value.firstOrNull { it.name == name }
        val windowInfo = windows.value.firstOrNull { it.name == name }
        return WindowUiState(
            name = name,
            windowInfo = mutableStateOf(windowInfo),
            style = entity?.getStyle(colorPalette.value) ?: SAFE_DEFAULT_STYLE,
            font = entity?.font,
            monoFont = entity?.monoFont,
            width = entity?.width,
            height = entity?.height,
            nameFilter = entity?.nameFilter ?: false,
            data = createWindowData(windowInfo?.windowType, name),
        )
    }

    /**
     * Whether a window's layout may be written to the character's settings. A transient panel is
     * still moved, resized and closed in the running layout, it just leaves nothing behind. Windows
     * the game has not told us about are treated as savable, so a stream window still restores where
     * the user put it if its info has not arrived yet.
     */
    private fun canSaveWindow(name: String): Boolean = windows.value.firstOrNull { it.name == name }?.resident != false

    /**
     * The user closing a window. It is remembered as hidden, so the game cannot bring it back with an
     * `openDialog` - the user has to ask for it again.
     */
    fun closeWindow(name: String) {
        removeWindow(name = name, hide = true)
        notifyPanelVisibility(name, open = false)
    }

    /**
     * The game closing a panel with a `closeDialog` tag. It leaves the layout but is not hidden: the
     * game is free to open it again later.
     *
     * Closes wait on the same gate as game opens: one that ran mid-restore would remove nothing
     * from the not-yet-populated dock while nulling the saved row underneath it, and a close
     * running ahead of a parked open for the same window would invert the protocol's order (the
     * stamp check settles who acts).
     */
    private fun closeWindowFromGame(name: String) {
        val stamp = stampGameWindowEvent(name)
        viewModelScope.launch {
            // No character means no restore is pending (it only runs for an identified
            // character), and the gate would never complete - act immediately.
            if (client.characterId.value != null) {
                if (withTimeoutOrNull(10.seconds) { layoutRestored.await() } == null) {
                    logger.w { "Layout restore still running after 10s; closing $name against it" }
                }
            }
            if (latestGameWindowEvent[name] != stamp) return@launch
            removeWindow(name = name, hide = false)
        }
    }

    private fun removeWindow(
        name: String,
        hide: Boolean,
    ) {
        _windowUiStates.update { states -> states.filter { it.name != name } }
        if (!canSaveWindow(name)) return
        viewModelScope.launch {
            client.characterId.value?.let { characterId ->
                if (hide) {
                    windowSettingsRepository.closeWindow(characterId = characterId, name = name)
                } else {
                    windowSettingsRepository.removeWindowFromLayout(characterId = characterId, name = name)
                }
            }
        }
    }

    fun clearStream(name: String) {
        viewModelScope.launch {
            val stream = windowRegistry.getOrCreateStream(name)
            stream.clear()
        }
    }

    /**
     * A [WindowUiState] for any window by [name], built on demand from the window registry and the
     * saved per-window settings. Lets the mobile phone/tablet tab layouts render a stream without
     * "opening" it into a dock. The main window returns its canonical, event-updated ui state.
     */
    fun streamWindowUiState(name: String): WindowUiState {
        if (name == "main") return _mainWindowUiState.value
        return tabWindowUiStates.getOrPut(name) {
            val entity = windowSettings.value.firstOrNull { it.name == name }
            val windowInfo = windows.value.firstOrNull { it.name == name }
            WindowUiState(
                name = name,
                windowInfo = mutableStateOf(windowInfo),
                style = entity?.getStyle(colorPalette.value) ?: SAFE_DEFAULT_STYLE,
                font = entity?.font,
                monoFont = entity?.monoFont,
                width = null,
                height = null,
                nameFilter = entity?.nameFilter ?: false,
                data = createWindowData(windowInfo?.windowType, name),
            )
        }
    }

    /** The tablet layout's secondary (non-main) tabbed pane location; defaults to the right. */
    fun observeTabletWindowLocation(): Flow<WindowLocation> =
        observePerCharacter { characterId ->
            characterSettingsRepository.observe(characterId, TABLET_WINDOW_LOCATION_KEY).map { value ->
                value?.let { runCatching { WindowLocation.valueOf(it) }.getOrNull() } ?: WindowLocation.RIGHT
            }
        }

    fun setTabletWindowLocation(location: WindowLocation) {
        viewModelScope.launch {
            client.characterId.value?.let { characterId ->
                characterSettingsRepository.save(characterId, TABLET_WINDOW_LOCATION_KEY, location.name)
            }
        }
    }

    /** The character's saved docking-layout JSON, or null when none has been saved yet. */
    suspend fun loadDockLayout(): String? =
        client.characterId.value?.let { characterId ->
            characterSettingsRepository.get(characterId, DOCK_LAYOUT_KEY)
        }

    /** Persists the docking-layout JSON. Layout churn is debounced by the caller. */
    fun saveDockLayout(layout: String) {
        viewModelScope.launch {
            client.characterId.value?.let { characterId ->
                characterSettingsRepository.save(characterId, DOCK_LAYOUT_KEY, layout)
            }
        }
    }

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
        // If a reconnect is still in flight (e.g. the user returned to the dashboard while it was
        // running), cancel it first so the in-progress attempt is unwound: cancellation runs the
        // connect use case's finally blocks, which close any half-opened client/socket so nothing leaks.
        reconnectJob?.let { job ->
            reconnectJob = null
            job.cancelAndJoin()
        }
        closeClient()
    }

    /** Tear down this game's client and window registry without touching any reconnect in flight. */
    private suspend fun closeClient() {
        if (!client.disconnected.value) {
            client.sendCommandDirect("quit")
        }
        client.close()
        windowRegistry.close()
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
                    // The reconnect installed a fresh game screen; release this (old) client.
                    closeClient()
                } finally {
                    _reconnecting.value = false
                }
            }
    }

    fun getCurrentTime(): Instant = client.getCurrentTime()

    /*
     * returns true when the command triggers type ahead
     */
    private suspend fun commandHandler(line: String): SendCommandType {
        val aliasedLine = applyAliases(line)
        return if (aliasedLine.startsWith(scriptCommandPrefix.value)) {
            val scriptCommand = aliasedLine.drop(scriptCommandPrefix.value.length)
            client.print(StyledString(aliasedLine, WarlockStyle.Command))
            scriptManager.startScript(client, scriptCommand, ::commandHandler)
            SendCommandType.SCRIPT
        } else if (aliasedLine.startsWith(CLIENT_COMMAND_PREFIX)) {
            client.print(StyledString(aliasedLine, WarlockStyle.Command))
            val clientCommand = aliasedLine.drop(1)
            val (command, args) = clientCommand.splitFirstWord()
            when (command) {
                "kill" -> {
                    // TODO: verify arguments
                    args?.split(' ')?.forEach { name ->
                        val script = scriptManager.findScriptInstance(name)
                        if (script != null) {
                            script.stop()
                            client.print(StyledString("Script $name stopped.", WarlockStyle.Echo))
                        } else {
                            client.print(StyledString("Script $name not found.", WarlockStyle.Error))
                        }
                    }
                }

                "pause" -> {
                    // TODO: verify arguments
                    args?.split(' ')?.forEach { name ->
                        val script = scriptManager.findScriptInstance(name)
                        if (script != null) {
                            script.suspend()
                            client.print(StyledString("Script $name paused.", WarlockStyle.Echo))
                        } else {
                            client.print(StyledString("Script $name not found.", WarlockStyle.Error))
                        }
                    }
                }

                "resume" -> {
                    // TODO: verify arguments
                    args?.split(' ')?.forEach { name ->
                        val script = scriptManager.findScriptInstance(name)
                        if (script != null) {
                            script.resume()
                            client.print(StyledString("Script $name resumed.", WarlockStyle.Echo))
                        } else {
                            client.print(StyledString("Script $name not found.", WarlockStyle.Error))
                        }
                    }
                }

                "list" -> {
                    val scripts = scriptManager.runningScripts.value
                    if (scripts.isEmpty()) {
                        client.print(StyledString("No scripts are running", WarlockStyle.Echo))
                    } else {
                        client.print(StyledString("Running scripts:", WarlockStyle.Echo))
                        scripts.forEach {
                            client.print(StyledString("${it.value.instance.name} - ${it.key}", WarlockStyle.Echo))
                        }
                    }
                }

                "disconnect", "dc" -> {
                    client.disconnect()
                }

                "send" -> {
                    client.sendCommandDirect(args ?: "")
                }

                else -> {
                    client.print(StyledString("Invalid command.", WarlockStyle.Error))
                }
            }
            SendCommandType.ACTION
        } else {
            client.sendCommand(aliasedLine)
            SendCommandType.COMMAND
        }
    }

    fun saveEntryStyle(style: StyleDefinition) {
        viewModelScope.launch {
            client.characterId.value?.let { characterId ->
                presetRepository.save(characterId = characterId, key = "entry", style = style)
            }
        }
    }

    fun handledMacroError() {
        _macroError.value = null
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
        entrySetSelection(TextRange(pos))
    }
}

/**
 * UI state for readline-style reverse history search. [query] is what the user has typed so far and
 * [match] is the history command currently matched (or null if nothing matches). While this is
 * non-null the entry prompt shows `searching "<query>": <match>`.
 */
data class HistorySearchState(
    val query: String,
    val match: String?,
)

/** A single find-in-window match: the line's serial number and the matched character range in it. */
private data class FindMatch(
    val serialNumber: Long,
    val range: IntRange,
)
