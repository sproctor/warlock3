package warlockfe.warlock3.compose.ui.game

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.delete
import androidx.compose.foundation.text.input.insert
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.mutableStateOf
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
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
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
import warlockfe.warlock3.compose.components.CompassState
import warlockfe.warlock3.compose.macros.macroCommands
import warlockfe.warlock3.compose.ui.window.ComposeDialogState
import warlockfe.warlock3.compose.ui.window.ComposeTextStream
import warlockfe.warlock3.compose.ui.window.DialogWindowData
import warlockfe.warlock3.compose.ui.window.ScrollEvent
import warlockfe.warlock3.compose.ui.window.StreamWindowData
import warlockfe.warlock3.compose.ui.window.WindowUiState
import warlockfe.warlock3.compose.ui.window.getStyle
import warlockfe.warlock3.compose.util.openUrl
import warlockfe.warlock3.core.client.ClientCompassEvent
import warlockfe.warlock3.core.client.ClientOpenUrlEvent
import warlockfe.warlock3.core.client.ClientWindowInfoEvent
import warlockfe.warlock3.core.client.GameCharacter
import warlockfe.warlock3.core.client.SendCommandType
import warlockfe.warlock3.core.client.WarlockAction
import warlockfe.warlock3.core.client.WarlockClient
import warlockfe.warlock3.core.macro.MacroKeyCombo
import warlockfe.warlock3.core.macro.MacroToken
import warlockfe.warlock3.core.macro.parseMacro
import warlockfe.warlock3.core.prefs.repositories.AliasRepository
import warlockfe.warlock3.core.prefs.repositories.CharacterSettingsRepository
import warlockfe.warlock3.core.prefs.repositories.MacroRepository
import warlockfe.warlock3.core.prefs.repositories.PresetRepository
import warlockfe.warlock3.core.prefs.repositories.VariableRepository
import warlockfe.warlock3.core.prefs.repositories.WindowSettingsRepository
import warlockfe.warlock3.core.prefs.repositories.defaultMaxTypeAhead
import warlockfe.warlock3.core.prefs.repositories.defaultStyles
import warlockfe.warlock3.core.prefs.repositories.maxTypeAheadKey
import warlockfe.warlock3.core.prefs.repositories.scriptCommandPrefixKey
import warlockfe.warlock3.core.script.ScriptManager
import warlockfe.warlock3.core.script.ScriptStatus
import warlockfe.warlock3.core.text.Alias
import warlockfe.warlock3.core.text.StyleDefinition
import warlockfe.warlock3.core.text.StyledString
import warlockfe.warlock3.core.text.WarlockStyle
import warlockfe.warlock3.core.util.splitFirstWord
import warlockfe.warlock3.core.window.WindowLocation
import warlockfe.warlock3.core.window.WindowRegistry
import warlockfe.warlock3.core.window.WindowType
import kotlin.math.max

const val clientCommandPrefix = '/'

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
    private val windowRegistry: WindowRegistry,
    private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val logger = KotlinLogging.logger { }

    val entryText = TextFieldState()

    private val _scrollEvents = MutableStateFlow<PersistentList<ScrollEvent>>(persistentListOf())
    val scrollEvents = _scrollEvents.asStateFlow()

    private val _compassState = MutableStateFlow(CompassState(emptySet()))
    val compassState: StateFlow<CompassState> = _compassState

    val vitalBars: ComposeDialogState = windowRegistry.getOrCreateDialog("minivitals") as ComposeDialogState

    val indicators = client.indicators
    val leftHand = client.leftHand
    val rightHand = client.rightHand
    val spellHand = client.spellHand

    private val _macroError = MutableStateFlow<String?>(null)
    val macroError = _macroError.asStateFlow()

    // Saved by macros
    private var storedText: String = ""

    val character = combine(
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

    val windowSettings = client.characterId.flatMapLatest { characterId ->
        if (characterId != null) {
            windowSettingsRepository.observeWindowSettings(characterId)
        } else {
            flow {}
        }
    }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val openWindows = windowSettings.map { currentWindowSettings ->
        currentWindowSettings.mapNotNull { entity ->
            entity.takeIf { it.position != null }?.name
        }
    }

    val windows = client.windowInfo

    val scriptCommandPrefix = client.characterId.flatMapLatest { characterId ->
        if (characterId != null) {
            characterSettingsRepository.observe(characterId = characterId, key = scriptCommandPrefixKey)
                .map { it ?: "." }
        } else {
            flow {}
        }
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = "."
        )

    val topHeight = client.characterId.flatMapLatest { characterId ->
        if (characterId != null) {
            characterSettingsRepository.observe(characterId = characterId, "topHeight")
                .map { it?.toIntOrNull() ?: 200 }
        } else {
            flow { }
        }
    }

    val bottomHeight = client.characterId.flatMapLatest { characterId ->
        if (characterId != null) {
            characterSettingsRepository.observe(characterId = characterId, "bottomHeight")
                .map { it?.toIntOrNull() ?: 200 }
        } else {
            flow { }
        }
    }

    val leftWidth = client.characterId.flatMapLatest { characterId ->
        if (characterId != null) {
            characterSettingsRepository.observe(characterId = characterId, "leftWidth")
                .map { it?.toIntOrNull() ?: 200 }
        } else {
            flow { }
        }
    }

    val rightWidth = client.characterId.flatMapLatest { characterId ->
        if (characterId != null) {
            characterSettingsRepository.observe(characterId = characterId, "rightWidth")
                .map { it?.toIntOrNull() ?: 200 }
        } else {
            flow { }
        }
    }

    private val macros = client.characterId.flatMapLatest { characterId ->
        if (characterId != null) {
            macroRepository.observeCharacterMacros(characterId)
        } else {
            macroRepository.observeGlobalMacros()
        }
            .map { macroCommands ->
                macroCommands.associate { it.keyCombo to it.command }
            }
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyMap()
        )

    private val variables: StateFlow<Map<String, String>> =
        client.characterId.flatMapLatest { characterId ->
            if (characterId != null) {
                variableRepository.observeCharacterVariables(characterId).map { list ->
                    list.associate { it.name to it.value }
                }
            } else {
                flow<Map<String, String>> { }
            }
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = emptyMap()
            )

    private val aliases: StateFlow<List<Alias>> = client.characterId.flatMapLatest { characterId ->
        if (characterId != null) {
            aliasRepository.observeForCharacter(characterId)
        } else {
            flow { }
        }
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    val presets = windowRegistry.presets

    private val runningScripts =
        scriptManager.runningScripts.stateIn(viewModelScope, SharingStarted.Eagerly, persistentMapOf())

    private val currentTime: Flow<Long> = flow {
        while (true) {
            val time = client.time
            emit(time / 1000L)
            val nextSecond = 1000L - (time % 1000)
            delay(max(10L, nextSecond))
        }
    }

    val roundTime = combine(currentTime, client.roundTime) { currentTime, roundTime ->
        val roundEnd = roundTime ?: 0
        max(0, roundEnd - currentTime).toInt()
    }
        .stateIn(scope = viewModelScope, started = SharingStarted.Eagerly, initialValue = 0)

    val castTime = combine(currentTime, client.castTime) { currentTime, castTime ->
        val roundEnd = castTime ?: 0
        max(0, roundEnd - currentTime).toInt()
    }
        .stateIn(scope = viewModelScope, started = SharingStarted.Eagerly, initialValue = 0)

    private var historyPosition = 0
    private val sendHistory = mutableListOf("")

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

    private val _mainWindowUiState = MutableStateFlow<WindowUiState>(
        WindowUiState(
            name = "main",
            windowInfo = mutableStateOf(windows.value.firstOrNull { it.name == "main" }),
            style = defaultStyles["default"]!!,
            data = StreamWindowData(
                stream = windowRegistry.getOrCreateStream("main") as ComposeTextStream,
            ),
            width = null,
            height = null,
        )
    )
    val mainWindowUiState: StateFlow<WindowUiState> = _mainWindowUiState.asStateFlow()

    private val _selectedWindow: MutableStateFlow<String> = MutableStateFlow("main")
    val selectedWindow: StateFlow<String> = _selectedWindow

    private var minHistoryLen = 0

    val disconnected = client.disconnected

    val menuData = client.menuData

    init {
        // Load initial
        client.characterId
            .onEach { characterId ->
                if (characterId != null) {
                    val settings = windowSettingsRepository.observeWindowSettings(characterId).first()
                    settings.filter { it.location != null }.forEach { entity ->
                        logger.debug { "Loading entity: $entity" }
                        val window = windows.value.firstOrNull { it.name == entity.name }
                        val uiState = WindowUiState(
                            name = entity.name,
                            windowInfo = mutableStateOf(window),
                            style = entity.getStyle(),
                            width = entity.width,
                            height = entity.height,
                            data = when (window?.windowType) {
                                WindowType.STREAM -> StreamWindowData(
                                    windowRegistry.getOrCreateStream(window.name) as ComposeTextStream,
                                )

                                WindowType.DIALOG ->
                                    DialogWindowData(windowRegistry.getOrCreateDialog(window.name) as ComposeDialogState)

                                else -> null
                            },
                        )
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
            }
            .launchIn(viewModelScope)
        windowSettings
            .onEach { currentWindowSettings ->
                currentWindowSettings.forEach { singleWindowSettings ->
                    if (singleWindowSettings.name == "main") {
                        _mainWindowUiState.update {
                            it.copy(style = singleWindowSettings.getStyle())
                        }
                    } else {
                        windowUiStateLists.forEach { stateList ->
                            stateList.update { states ->
                                val index = states.indexOfFirst { it.name == singleWindowSettings.name }
                                if (index != -1) {
                                    val mutableStates = states.toMutableList()
                                    mutableStates[index] = states[index].copy(style = singleWindowSettings.getStyle())
                                    mutableStates
                                } else {
                                    states
                                }
                            }
                        }
                    }
                }
            }
            .launchIn(viewModelScope)
        client.eventFlow
            .onEach { event ->
                when (event) {

                    is ClientCompassEvent -> {
                        _compassState.value = CompassState(directions = event.directions.toSet())
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
                                                mutableStates[index] = uiState.copy(
                                                    data = when (event.info.windowType) {
                                                        WindowType.STREAM -> StreamWindowData(
                                                            windowRegistry.getOrCreateStream(event.info.name) as ComposeTextStream,
                                                        )

                                                        WindowType.DIALOG ->
                                                            DialogWindowData(windowRegistry.getOrCreateDialog(event.info.name) as ComposeDialogState)
                                                    }
                                                )
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
            }
            .launchIn(viewModelScope)

        client.characterId.transformLatest {
            if (it != null) {
                emitAll(characterSettingsRepository.observe(it, maxTypeAheadKey))
            }
        }
            .onEach { maxTypeAhead ->
                client.setMaxTypeAhead(maxTypeAhead?.toIntOrNull() ?: defaultMaxTypeAhead)
            }
            .launchIn(viewModelScope)

        runningScripts
            .onEach { scripts ->
                val scriptStream = client.getStream("warlockscripts")
                scriptStream.clear()
                scripts.forEach { entry ->
                    val instance = entry.value.instance
                    var text = StyledString("${instance.name}: ${instance.status} ")
                    when (instance.status) {
                        ScriptStatus.Running -> text += StyledString(
                            "pause",
                            WarlockStyle.Link(WarlockAction.SendCommand("/pause ${entry.key}"))
                        )

                        ScriptStatus.Suspended -> text += StyledString(
                            "resume",
                            WarlockStyle.Link(WarlockAction.SendCommand("/resume ${entry.key}"))
                        )

                        else -> {
                            // do nothing
                        }
                    }
                    text += StyledString(" ") +
                            StyledString("stop", WarlockStyle.Link(WarlockAction.SendCommand("/kill ${entry.key}")))
                    scriptStream.appendLine(text, false)
                }
            }
            .launchIn(viewModelScope)
    }

    fun submit() {
        var line = entryText.text.toString()
        entryText.clearText()
        updateHistory(line)
        historyPosition = 0
        line = applyAliases(line)
        sendCommand(line)
    }

    private fun applyAliases(line: String): String {
        return aliases.value.fold(line) { acc, alias -> alias.replace(acc) }
    }

    private fun updateHistory(line: String) {
        if (line.length >= minHistoryLen && sendHistory.getOrNull(1) != line) {
            sendHistory[0] = line
            sendHistory.add(0, "")
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

    suspend fun stopScripts() {
        val scripts = scriptManager.runningScripts.value.values
        val count = scripts.size
        if (count > 0) {
            scripts.forEach { script ->
                script.instance.stop()
            }
            client.print(StyledString("Stopped $count script(s)"))
        }
    }

    suspend fun pauseScripts() {
        val scriptInstances = scriptManager.runningScripts.value.values.map { it.instance }
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

    suspend fun repeatCommand(index: Int) {
        val command = sendHistory.getOrNull(index)
        if (command != null) {
            client.sendCommand(command)
        }
    }

    fun runScript(file: Path) {
        viewModelScope.launch(ioDispatcher) {
            scriptManager.startScript(client, file, ::commandHandler)
        }
    }

    fun handleKeyPress(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) {
            return false
        }

        val keyString = translateKeyPress(event)
        val macroString = macros.value[keyString]

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
                        moveCursor = entryText.selection.min
                    }

                    is MacroToken.Text -> {
                        entryInsert(token.text)
                    }

                    is MacroToken.Variable -> {
                        variables.value[token.name]?.let {
                            entryInsert(it)
                        }
                    }

                    is MacroToken.Command -> {
                        val command = macroCommands[token.name]
                        if (command != null) {
                            command(this@GameViewModel)
                        } else {
                            _macroError.value = "Macro command not found: ${token.name}"
                        }
                    }
                }
            }
            if (moveCursor != null) {
                entryText.edit {
                    selection = TextRange(moveCursor!!)
                }
            }
        }
    }

    private suspend fun handleEntity(entity: Char, onEntryCleared: () -> Unit) {
        when (entity) {
            'x' -> {
                storedText = entryText.text.toString()
                entryText.clearText()
                onEntryCleared()
            }

            'r' -> {
                val line = entryText.text.toString()
                entryText.clearText()
                updateHistory(line)
                historyPosition = 0
                val aliasedLine = applyAliases(line)
                commandHandler(aliasedLine)
                onEntryCleared()
            }

            'p' -> {
                delay(1_000L)
            }

            '?' -> {
                entryText.edit {
                    append(storedText)
                }
            }
        }
    }

    // Must be called from main thread
    fun entryDelete(range: TextRange) {
        entryText.edit {
            delete(range.min, range.max)
        }
    }

    // Must be called from main thread
    fun entrySetSelection(selection: TextRange) {
        entryText.edit {
            this.selection = selection
        }
    }

    fun entryInsert(text: String) {
        entryText.edit {
            if (selection.length > 0) {
                delete(selection.min, selection.max)
            }
            insert(selection.min, text)
        }
    }

    fun historyPrev() {
        val history = sendHistory
        if (historyPosition < history.size - 1) {
            sendHistory[historyPosition] = entryText.text.toString()
            historyPosition++
            entryText.setTextAndPlaceCursorAtEnd(history[historyPosition])
        }
    }

    fun historyNext() {
        if (historyPosition > 0) {
            sendHistory[historyPosition] = entryText.text.toString()
            historyPosition--
            entryText.setTextAndPlaceCursorAtEnd(sendHistory[historyPosition])
        }
    }

    // TODO: convert this into a simpler representation
    private fun translateKeyPress(event: KeyEvent): MacroKeyCombo {
        return MacroKeyCombo(
            keyCode = event.key.keyCode,
            ctrl = event.isCtrlPressed,
            alt = event.isAltPressed,
            shift = event.isShiftPressed,
            meta = event.isMetaPressed,
        )
    }

    // TODO: listen to WindowUIStates to implement these
    fun moveWindow(name: String, location: WindowLocation) {
        val uiState = windowUiStateLists.flatMap { it.value }.firstOrNull { it.name == name } ?: return
        windowUiStateLists.forEach { uiState ->
            uiState.update { state ->
                state.filter { it.name != name }
            }
        }
        val windowUiStates = getWindowUiStatesForLocation(location)
        windowUiStates.update { states ->
            states + uiState
        }
        viewModelScope.launch {
            client.characterId.value?.let { characterId ->
                windowSettingsRepository.moveWindow(
                    characterId = characterId,
                    name = name,
                    location = location
                )
            }
        }
    }

    fun setWindowWidth(name: String, width: Int) {
        viewModelScope.launch {
            client.characterId.value?.let { characterId ->
                windowSettingsRepository.setWindowWidth(
                    characterId = characterId,
                    name = name,
                    width = width
                )
            }
        }
    }

    fun setWindowHeight(name: String, height: Int) {
        viewModelScope.launch {
            client.characterId.value?.let { characterId ->
                windowSettingsRepository.setWindowHeight(
                    characterId = characterId,
                    name = name,
                    height = height
                )
            }
        }
    }

    fun setLocationSize(location: WindowLocation, size: Int) {
        client.characterId.value?.let { characterId ->
            viewModelScope.launch {
                val key = when (location) {
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

    fun changeWindowPositions(location: WindowLocation, fromIndex: Int, toIndex: Int) {
        val windowUiStates = getWindowUiStatesForLocation(location)
        windowUiStates.update { states ->
            val mutableStates = states.toMutableList()
            val item = mutableStates.removeAt(fromIndex)
            mutableStates.add(toIndex, item)
            mutableStates
        }
        viewModelScope.launch {
            client.characterId.value?.let { characterId ->
                logger.debug { "Moving window at $location from $fromIndex to $toIndex" }
                for (index in fromIndex..toIndex) {
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
                newState = WindowUiState(
                    name = name,
                    windowInfo = mutableStateOf(windowInfo),
                    style = entity?.getStyle() ?: defaultStyles["default"]!!,
                    width = null,
                    height = null,
                    data = when (windowInfo?.windowType) {
                        WindowType.STREAM -> StreamWindowData(
                            stream = windowRegistry.getOrCreateStream(name) as ComposeTextStream,
                        )

                        WindowType.DIALOG -> DialogWindowData(
                            dialogData = windowRegistry.getOrCreateDialog(name) as ComposeDialogState
                        )

                        else -> null
                    },
                )
                states + newState
            }
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

    fun saveWindowStyle(name: String, style: StyleDefinition) {
        viewModelScope.launch {
            client.characterId.value?.let { characterId ->
                windowSettingsRepository.setStyle(characterId = characterId, name = name, style = style)
            }
        }
    }

    fun selectWindow(window: String) {
        _selectedWindow.value = window
    }

    fun scroll(event: ScrollEvent) {
        _scrollEvents.update { it.add(event) }
    }

    fun handledScrollEvent(event: ScrollEvent) {
        _scrollEvents.update { oldList ->
            oldList.remove(event)
        }
    }

    suspend fun close() {
        if (!client.disconnected.value) {
            client.sendCommandDirect("quit")
        }
        client.close()
    }

    /*
     * returns true when the command triggers type ahead
     */
    private suspend fun commandHandler(line: String): SendCommandType {
        return if (line.startsWith(scriptCommandPrefix.value)) {
            val scriptCommand = line.drop(1)
            client.print(StyledString(line, WarlockStyle.Command))
            scriptManager.startScript(client, scriptCommand, ::commandHandler)
            SendCommandType.SCRIPT
        } else if (line.startsWith(clientCommandPrefix)) {
            client.print(StyledString(line, WarlockStyle.Command))
            val clientCommand = line.drop(1)
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
            client.sendCommand(line)
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

    private fun getWindowUiStatesForLocation(location: WindowLocation): MutableStateFlow<List<WindowUiState>> {
        return when (location) {
            WindowLocation.LEFT -> _leftWindowUiStates
            WindowLocation.RIGHT -> _rightWindowUiStates
            WindowLocation.TOP -> _topWindowUiStates
            WindowLocation.BOTTOM -> _bottomWindowUiStates
            else -> error("Change position error: Invalid window location")
        }
    }
}
