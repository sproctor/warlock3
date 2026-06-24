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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
import kotlinx.io.files.Path
import warlockfe.warlock3.compose.ui.window.ComposeDialogState
import warlockfe.warlock3.compose.ui.window.ComposeTextStream
import warlockfe.warlock3.compose.ui.window.DialogWindowData
import warlockfe.warlock3.compose.ui.window.StreamTextLine
import warlockfe.warlock3.compose.ui.window.StreamWindowData
import warlockfe.warlock3.compose.ui.window.WindowData
import warlockfe.warlock3.compose.ui.window.WindowFindController
import warlockfe.warlock3.compose.ui.window.WindowFindUiState
import warlockfe.warlock3.compose.ui.window.WindowUiState
import warlockfe.warlock3.compose.ui.window.getStyle
import warlockfe.warlock3.compose.util.SAFE_DEFAULT_STYLE
import warlockfe.warlock3.compose.util.openUrl
import warlockfe.warlock3.core.client.ClientCompassEvent
import warlockfe.warlock3.core.client.ClientOpenUrlEvent
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
import warlockfe.warlock3.core.text.StyleDefinition
import warlockfe.warlock3.core.text.StyledString
import warlockfe.warlock3.core.text.WarlockColor
import warlockfe.warlock3.core.text.WarlockStyle
import warlockfe.warlock3.core.util.splitFirstWord
import warlockfe.warlock3.core.window.WindowLocation
import warlockfe.warlock3.core.window.WindowRegistry
import warlockfe.warlock3.core.window.WindowType
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

const val CLIENT_COMMAND_PREFIX = '/'

// Per-character setting key for the tablet layout's secondary (non-main) tabbed pane location.
const val TABLET_WINDOW_LOCATION_KEY = "tabletWindowLocation"

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

    val vitalBars: ComposeDialogState = windowRegistry.getOrCreateDialog("minivitals") as ComposeDialogState

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
                entity.takeIf { it.position != null }?.name
            }
        }

    val windows = client.windowInfo

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

    private val runningScripts =
        scriptManager.runningScripts.stateIn(viewModelScope, SharingStarted.Eagerly, persistentMapOf())

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

    private val _leftWindowUiStates = MutableStateFlow<List<WindowUiState>>(emptyList())
    val leftWindowUiStates: StateFlow<List<WindowUiState>> = _leftWindowUiStates.asStateFlow()

    private val _rightWindowUiStates = MutableStateFlow<List<WindowUiState>>(emptyList())
    val rightWindowUiStates: StateFlow<List<WindowUiState>> = _rightWindowUiStates.asStateFlow()

    private val _topWindowUiStates = MutableStateFlow<List<WindowUiState>>(emptyList())
    val topWindowUiStates: StateFlow<List<WindowUiState>> = _topWindowUiStates.asStateFlow()

    private val _bottomWindowUiStates = MutableStateFlow<List<WindowUiState>>(emptyList())
    val bottomWindowUiStates: StateFlow<List<WindowUiState>> = _bottomWindowUiStates.asStateFlow()

    private val windowUiStateLists
        get() = listOf(_leftWindowUiStates, _rightWindowUiStates, _topWindowUiStates, _bottomWindowUiStates)

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
            WindowType.DIALOG -> DialogWindowData(windowRegistry.getOrCreateDialog(name) as ComposeDialogState)
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
                    val settings = windowSettingsRepository.observeWindowSettings(characterId).first()
                    settings.filter { it.location != null }.forEach { entity ->
                        logger.d { "Loading entity: $entity" }
                        val window = windows.value.firstOrNull { it.name == entity.name }
                        val uiState =
                            WindowUiState(
                                name = entity.name,
                                windowInfo = mutableStateOf(window),
                                style = entity.getStyle(),
                                width = entity.width,
                                height = entity.height,
                                nameFilter = entity.nameFilter,
                                data = createWindowData(window?.windowType, entity.name),
                            )
                        (uiState.data as? StreamWindowData)?.stream?.setNameFilter(entity.nameFilter)
                        when (entity.location) {
                            WindowLocation.MAIN -> _mainWindowUiState.value = uiState
                            WindowLocation.TOP -> _topWindowUiStates.update { it + uiState }
                            WindowLocation.BOTTOM -> _bottomWindowUiStates.update { it + uiState }
                            WindowLocation.LEFT -> _leftWindowUiStates.update { it + uiState }
                            WindowLocation.RIGHT -> _rightWindowUiStates.update { it + uiState }
                            else -> Unit // Nothing to do
                        }
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
                                style = singleWindowSettings.getStyle(),
                                nameFilter = singleWindowSettings.nameFilter,
                            )
                        }
                    } else {
                        windowUiStateLists.forEach { stateList ->
                            stateList.update { states ->
                                val index = states.indexOfFirst { it.name == singleWindowSettings.name }
                                if (index != -1) {
                                    val mutableStates = states.toMutableList()
                                    (states[index].data as? StreamWindowData)
                                        ?.stream
                                        ?.setNameFilter(singleWindowSettings.nameFilter)
                                    mutableStates[index] =
                                        states[index].copy(
                                            style = singleWindowSettings.getStyle(),
                                            nameFilter = singleWindowSettings.nameFilter,
                                        )
                                    mutableStates
                                } else {
                                    states
                                }
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

                    is ClientWindowInfoEvent -> {
                        if (event.info.name == "main") {
                            _mainWindowUiState.value.windowInfo.value = event.info
                        } else {
                            windowUiStateLists.forEach { windowUiStates ->
                                windowUiStates.value
                                    .indexOfFirst { it.name == event.info.name }
                                    .takeIf { it != -1 }
                                    ?.let { index ->
                                        val uiState = windowUiStates.value[index]
                                        uiState.windowInfo.value = event.info
                                        if (uiState.data == null) {
                                            windowUiStates.update { states ->
                                                val mutableStates = states.toMutableList()
                                                mutableStates[index] =
                                                    uiState.copy(
                                                        data = createWindowData(event.info.windowType, event.info.name),
                                                    )
                                                (mutableStates[index].data as? StreamWindowData)
                                                    ?.stream
                                                    ?.setNameFilter(uiState.nameFilter)
                                                mutableStates
                                            }
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
    // stream (e.g. a dialog window). Starts with an empty query, so nothing matches until the user
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

    fun moveWindowToPosition(
        name: String,
        targetLocation: WindowLocation,
        targetIndex: Int,
    ) {
        val uiState = windowUiStateLists.flatMap { it.value }.firstOrNull { it.name == name } ?: return
        windowUiStateLists.forEach { states ->
            states.update { state ->
                state.filter { it.name != name }
            }
        }
        val targetStates = getWindowUiStatesForLocation(targetLocation)
        targetStates.update { states ->
            val mutableStates = states.toMutableList()
            mutableStates.add(targetIndex.coerceAtMost(mutableStates.size), uiState)
            mutableStates
        }
        viewModelScope.launch {
            client.characterId.value?.let { characterId ->
                windowSettingsRepository.moveWindowToPosition(
                    characterId = characterId,
                    name = name,
                    location = targetLocation,
                    position = targetIndex,
                )
            }
        }
    }

    fun setWindowWidth(
        name: String,
        width: Int,
    ) {
        viewModelScope.launch {
            client.characterId.value?.let { characterId ->
                windowSettingsRepository.setWindowWidth(
                    characterId = characterId,
                    name = name,
                    width = width,
                )
            }
        }
    }

    fun setWindowHeight(
        name: String,
        height: Int,
    ) {
        viewModelScope.launch {
            client.characterId.value?.let { characterId ->
                windowSettingsRepository.setWindowHeight(
                    characterId = characterId,
                    name = name,
                    height = height,
                )
            }
        }
    }

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

    fun changeWindowPositions(
        location: WindowLocation,
        fromIndex: Int,
        toIndex: Int,
    ) {
        val windowUiStates = getWindowUiStatesForLocation(location)
        windowUiStates.update { states ->
            val mutableStates = states.toMutableList()
            val item = mutableStates.removeAt(fromIndex)
            val adjustedToIndex = if (toIndex > fromIndex) toIndex - 1 else toIndex
            mutableStates.add(adjustedToIndex, item)
            mutableStates
        }
        viewModelScope.launch {
            client.characterId.value?.let { characterId ->
                logger.d { "Moving window at $location from $fromIndex to $toIndex" }
                val clampedToIndex = toIndex.coerceAtMost(windowUiStates.value.size - 1)
                val range = if (fromIndex < clampedToIndex) fromIndex..clampedToIndex else clampedToIndex..fromIndex
                for (index in range) {
                    val name = windowUiStates.value[index].name
                    logger.d { "Setting window $name position to $index" }
                    windowSettingsRepository.setPosition(characterId, windowUiStates.value[index].name, index)
                }
            }
        }
    }

    fun openWindow(name: String) {
        var newState: WindowUiState? = null
        _topWindowUiStates.update { states ->
            if (states.any { it.name == name }) {
                states
            } else {
                val entity = windowSettings.value.firstOrNull { it.name == name }
                val windowInfo = windows.value.firstOrNull { it.name == name }
                newState =
                    WindowUiState(
                        name = name,
                        windowInfo = mutableStateOf(windowInfo),
                        style = entity?.getStyle() ?: SAFE_DEFAULT_STYLE,
                        width = null,
                        height = null,
                        nameFilter = entity?.nameFilter ?: false,
                        data = createWindowData(windowInfo?.windowType, name),
                    )
                states + newState
            }
        }
        newState?.let { state ->
            (state.data as? StreamWindowData)?.stream?.setNameFilter(state.nameFilter)
        }
        if (newState != null) {
            viewModelScope.launch {
                client.characterId.value?.let { characterId ->
                    windowSettingsRepository.openWindow(
                        characterId = characterId,
                        name = name,
                        location = WindowLocation.TOP,
                        position = _topWindowUiStates.value.lastIndex,
                    )
                }
            }
        }
    }

    fun closeWindow(name: String) {
        windowUiStateLists.forEach { windowUiStates ->
            windowUiStates.update { states -> states.filter { it.name != name } }
        }
        viewModelScope.launch {
            client.characterId.value?.let { characterId ->
                windowSettingsRepository.closeWindow(characterId = characterId, name = name)
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
                style = entity?.getStyle() ?: SAFE_DEFAULT_STYLE,
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

    private fun getWindowUiStatesForLocation(location: WindowLocation): MutableStateFlow<List<WindowUiState>> =
        when (location) {
            WindowLocation.LEFT -> _leftWindowUiStates
            WindowLocation.RIGHT -> _rightWindowUiStates
            WindowLocation.TOP -> _topWindowUiStates
            WindowLocation.BOTTOM -> _bottomWindowUiStates
            else -> error("Change position error: Invalid window location")
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
