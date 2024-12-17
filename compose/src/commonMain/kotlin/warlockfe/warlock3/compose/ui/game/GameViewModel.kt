package warlockfe.warlock3.compose.ui.game

import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import warlockfe.warlock3.compose.components.CompassState
import warlockfe.warlock3.compose.components.CompassTheme
import warlockfe.warlock3.compose.macros.getLabel
import warlockfe.warlock3.compose.macros.macroCommands
import warlockfe.warlock3.compose.macros.parseMacroCommand
import warlockfe.warlock3.compose.model.ViewHighlight
import warlockfe.warlock3.compose.ui.window.ComposeTextStream
import warlockfe.warlock3.compose.ui.window.WindowLine
import warlockfe.warlock3.compose.ui.window.WindowUiState
import warlockfe.warlock3.compose.util.getEntireLineStyles
import warlockfe.warlock3.compose.util.highlight
import warlockfe.warlock3.compose.util.toAnnotatedString
import warlockfe.warlock3.compose.util.toSpanStyle
import warlockfe.warlock3.core.client.ClientCompassEvent
import warlockfe.warlock3.core.client.ClientProgressBarEvent
import warlockfe.warlock3.core.client.GameCharacter
import warlockfe.warlock3.core.client.ProgressBarData
import warlockfe.warlock3.core.client.WarlockClient
import warlockfe.warlock3.core.macro.MacroToken
import warlockfe.warlock3.core.prefs.AliasRepository
import warlockfe.warlock3.core.prefs.CharacterSettingsRepository
import warlockfe.warlock3.core.prefs.HighlightRepository
import warlockfe.warlock3.core.prefs.MacroRepository
import warlockfe.warlock3.core.prefs.PresetRepository
import warlockfe.warlock3.core.prefs.VariableRepository
import warlockfe.warlock3.core.prefs.WindowRepository
import warlockfe.warlock3.core.prefs.defaultMaxTypeAhead
import warlockfe.warlock3.core.prefs.defaultStyles
import warlockfe.warlock3.core.prefs.maxTypeAheadKey
import warlockfe.warlock3.core.script.ScriptManager
import warlockfe.warlock3.core.text.Alias
import warlockfe.warlock3.core.text.StyleDefinition
import warlockfe.warlock3.core.text.StyledString
import warlockfe.warlock3.core.text.flattenStyles
import warlockfe.warlock3.core.window.StreamLine
import warlockfe.warlock3.core.window.StreamRegistry
import warlockfe.warlock3.core.window.WindowLocation
import java.io.File
import kotlin.math.max

@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModel(
    private val windowRepository: WindowRepository,
    val client: WarlockClient,
    val macroRepository: MacroRepository,
    val variableRepository: VariableRepository,
    highlightRepository: HighlightRepository,
    presetRepository: PresetRepository,
    private val scriptManager: ScriptManager,
    val compassTheme: CompassTheme,
    private val characterSettingsRepository: CharacterSettingsRepository,
    aliasRepository: AliasRepository,
    private val streamRegistry: StreamRegistry,
) : ViewModel() {

    private val logger = KotlinLogging.logger { }

    var entryText by mutableStateOf(TextFieldValue())
        private set

    private val _compassState = mutableStateOf(CompassState(emptySet()))
    val compassState: State<CompassState> = _compassState

    private val _vitalBars = mutableStateMapOf<String, ProgressBarData>()
    val vitalBars: SnapshotStateMap<String, ProgressBarData> = _vitalBars

    // Saved by macros
    private var storedText: String? = null

    val properties: StateFlow<Map<String, String>> = client.properties

    val character = combine(client.characterId, properties) { characterId, properties ->
        val game = properties["game"]
        val name = properties["character"]
        if (characterId != null && game != null && name != null) {
            GameCharacter(accountId = null, id = characterId, gameCode = game, name = name)
        } else {
            null
        }
    }

    val topHeight = client.characterId.flatMapLatest { characterId ->
        if (characterId != null) {
            characterSettingsRepository.observe(characterId = characterId, "topHeight")
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
            .map {
                it.toMap()
            }
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyMap()
        )

    val presets: Flow<Map<String, StyleDefinition>> =
        client.characterId.flatMapLatest { characterId ->
            if (characterId != null) {
                presetRepository.observePresetsForCharacter(characterId)
            } else {
                flow { emit(emptyMap()) }
            }
        }

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

    private var scriptsPaused = false

    private val currentTime: Flow<Int> = flow {
        while (true) {
            val time = client.time
            emit((time / 1000L).toInt())
            val nextSecond = 1000L - (time % 1000)
            delay(max(10L, nextSecond))
        }
    }

    val roundTime = combine(currentTime, properties) { currentTime, properties ->
        val roundEnd = properties["roundtime"]?.toIntOrNull() ?: 0
        max(0, roundEnd - currentTime)
    }
        .stateIn(scope = viewModelScope, started = SharingStarted.Eagerly, initialValue = 0)

    val castTime = combine(currentTime, properties) { currentTime, properties ->
        val roundEnd = properties["casttime"]?.toIntOrNull() ?: 0
        max(0, roundEnd - currentTime)
    }
        .stateIn(scope = viewModelScope, started = SharingStarted.Eagerly, initialValue = 0)

    private var historyPosition = -1
    private val sendHistory = mutableListOf<String>()

    private val windows = windowRepository.windows

    private val highlights: Flow<List<ViewHighlight>> = client.characterId.flatMapLatest { characterId ->
        if (characterId != null) {
            highlightRepository.observeForCharacter(characterId)
                .map { highlights ->
                    highlights.mapNotNull { highlight ->
                        val pattern = if (highlight.isRegex) {
                            highlight.pattern
                        } else {
                            val subpattern = Regex.escape(highlight.pattern)
                            if (highlight.matchPartialWord) {
                                subpattern
                            } else {
                                "\\b$subpattern\\b"
                            }
                        }
                        try {
                            ViewHighlight(
                                regex = Regex(
                                    pattern = pattern,
                                    options = if (highlight.ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet(),
                                ),
                                styles = highlight.styles.mapValues { it.value.toSpanStyle() }
                            )
                        } catch (e: Throwable) {
                            // TODO: notify about error
                            null
                        }
                    }
                }
        } else {
            flow {
                emit(emptyList())
            }
        }
    }

    private val openWindows = client.characterId.flatMapLatest {
        if (it != null) {
            windowRepository.observeOpenWindows(it)
        } else {
            flow { emit(emptySet()) }
        }
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptySet()
        )

    val windowUiStates: Flow<List<WindowUiState>> =
        combine(
            openWindows,
            windows,
            presets,
            highlights
        ) { openWindows, windows, presets, highlights ->
            openWindows.map { name ->
                WindowUiState(
                    name = name,
                    stream = streamRegistry.getOrCreateStream(name) as ComposeTextStream,
                    window = windows[name],
                    highlights = highlights,
                    presets = presets,
                    defaultStyle = presets["default"] ?: defaultStyles["default"]!!,
                    allowSelection = !name.equals("warlockscripts", true)
                )
            }
        }

    val mainWindowUiState: StateFlow<WindowUiState> =
        combine(windows, presets, highlights) { windows, presets, highlights ->
            val name = "main"
            WindowUiState(
                name = name,
                stream = streamRegistry.getOrCreateStream(name) as ComposeTextStream,
                window = windows[name],
                highlights = highlights,
                presets = presets,
                defaultStyle = presets["default"] ?: defaultStyles["default"]!!,
                allowSelection = true,
            )
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue =
                    WindowUiState(
                        name = "main",
                        stream = streamRegistry.getOrCreateStream("main") as ComposeTextStream,
                        window = null,
                        highlights = emptyList(),
                        presets = emptyMap(),
                        defaultStyle = defaultStyles["default"]!!,
                        allowSelection = true,
                    )
            )

    init {
        client.eventFlow
            .onEach { event ->
                when (event) {
                    is ClientProgressBarEvent -> {
                        _vitalBars += event.progressBarData.id to event.progressBarData
                    }

                    is ClientCompassEvent -> {
                        _compassState.value = CompassState(directions = event.directions.toSet())
                    }

                    else -> {
                        // don't care
                    }
                }
            }
            .launchIn(viewModelScope)

        character.transformLatest {
            if (it != null) {
                emitAll(characterSettingsRepository.observe(it.id, maxTypeAheadKey))
            }
        }
            .onEach { maxTypeAhead ->
                client.setMaxTypeAhead(maxTypeAhead?.toIntOrNull() ?: defaultMaxTypeAhead)
            }
            .launchIn(viewModelScope)
    }

    fun submit() {
        var line = entryText.text
        entryText = TextFieldValue()
        aliases.value.forEach { alias ->
            line = alias.replace(line)
        }
        sendHistory.add(0, line)
        historyPosition = -1
        sendCommand(line)
    }

    fun sendCommand(command: String) {
        viewModelScope.launch(Dispatchers.IO) {
            client.sendCommand(command)
        }
    }

    suspend fun stopScripts() {
        val scriptInstances = scriptManager.runningScripts
        val count = scriptInstances.size
        if (count > 0) {
            scriptInstances.forEach { scriptInstance ->
                scriptInstance.stop()
            }
            client.print(StyledString("Stopped $count script(s)"))
        }
    }

    suspend fun pauseScripts() {
        val paused = this.scriptsPaused
        this.scriptsPaused = !paused
        val scriptInstances = scriptManager.runningScripts
        if (scriptInstances.isNotEmpty()) {
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
        }
    }

    suspend fun repeatCommand(index: Int) {
        val command = sendHistory.getOrNull(index)
        if (command != null) {
            client.sendCommand(command)
        }
    }

    fun runScript(file: File) {
        viewModelScope.launch(Dispatchers.IO) {
            scriptManager.startScript(client, file)
        }
    }

    fun handleKeyPress(event: KeyEvent, clipboard: ClipboardManager): Boolean {
        if (event.type != KeyEventType.KeyDown) {
            return false
        }

        val keyString = translateKeyPress(event)
        val macroString = macros.value[keyString]

        if (macroString != null) {
            // TODO: notify when macro fails to parse
            val tokens = parseMacroCommand(macroString).getOrNull() ?: return false

            executeMacro(tokens, clipboard)

            return true
        }

        if (event.key.keyCode == Key.Enter.keyCode) {
            submit()
            return true
        }

        return false
    }

    private fun executeMacro(tokens: List<MacroToken>, clipboard: ClipboardManager) {
        viewModelScope.launch {
            var movedCursor = false
            tokens.forEach { token ->
                when (token) {
                    is MacroToken.Entity -> {
                        handleEntity(token.char)
                    }

                    MacroToken.At -> {
                        entryText = entryText.copy(selection = TextRange(entryText.text.length))
                        movedCursor = true
                    }

                    MacroToken.Question -> {
                        storedText?.let { entryAppend(it, !movedCursor) }
                    }

                    is MacroToken.Text -> {
                        entryAppend(token.text, !movedCursor)
                    }

                    is MacroToken.Variable -> {
                        entryAppend(variables.value[token.name] ?: "", !movedCursor)
                    }

                    is MacroToken.Command -> {
                        val command = macroCommands[token.name]
                        command?.invoke(this@GameViewModel, clipboard)
                    }
                }
            }
        }
    }

    private suspend fun handleEntity(entity: Char) {
        when (entity) {
            'x' -> {
                storedText = entryText.text
                entryClear()
            }

            'r' -> {
                submit()
            }

            'p' -> {
                delay(1_000L)
            }
        }
    }

    private fun entryClear() {
        entryText = TextFieldValue()
    }

    private fun entryAppend(text: String, moveCursor: Boolean) {
        val newText = entryText.text + text
        val selection = if (moveCursor) {
            TextRange(newText.length)
        } else {
            entryText.selection
        }
        entryText = entryText.copy(text = newText, selection = selection)
    }

    fun entryInsert(text: String) {
        val currentTextField = entryText
        val prefix = currentTextField.text.substring(0, currentTextField.selection.start)
        val postfix = currentTextField.text.substring(
            currentTextField.selection.end,
            currentTextField.text.length
        )
        val newText = prefix + text + postfix
        val pos = prefix.length + text.length
        entryText = currentTextField.copy(text = newText, selection = TextRange(pos))
    }

    fun historyPrev() {
        val history = sendHistory
        if (historyPosition < history.size - 1) {
            historyPosition++
            val text = history[historyPosition]
            entryText = TextFieldValue(text = text, selection = TextRange(text.length))
        }
    }

    fun historyNext() {
        if (historyPosition >= 0) {
            historyPosition--
            if (historyPosition < 0) {
                entryClear()
            } else {
                val text = sendHistory[historyPosition]
                entryText = TextFieldValue(text = text, selection = TextRange(text.length))
            }
        }
    }

    // TODO: convert this into a simpler representation
    private fun translateKeyPress(event: KeyEvent): String {
        val keyString = StringBuilder()
        if (event.isCtrlPressed) {
            keyString.append("ctrl+")
        }
        if (event.isAltPressed) {
            keyString.append("alt+")
        }
        if (event.isShiftPressed) {
            keyString.append("shift+")
        }
        if (event.isMetaPressed) {
            keyString.append("meta+")
        }
        keyString.append(event.key.getLabel())
        return keyString.toString()
    }

    fun updateEntryText(value: TextFieldValue) {
        entryText = value
    }

    fun moveWindow(name: String, location: WindowLocation) {
        client.characterId.value?.let { characterId ->
            viewModelScope.launch {
                windowRepository.moveWindow(
                    characterId = characterId,
                    name = name,
                    location = location
                )
            }
        }
    }

    fun setWindowWidth(name: String, width: Int) {
        client.characterId.value?.let { characterId ->
            viewModelScope.launch {
                windowRepository.setWindowWidth(
                    characterId = characterId,
                    name = name,
                    width = width
                )
            }
        }
    }

    fun setWindowHeight(name: String, height: Int) {
        client.characterId.value?.let { characterId ->
            viewModelScope.launch {
                windowRepository.setWindowHeight(
                    characterId = characterId,
                    name = name,
                    height = height
                )
            }
        }
    }

    fun setLeftWidth(width: Int) {
        client.characterId.value?.let { characterId ->
            viewModelScope.launch {
                characterSettingsRepository.save(characterId, "leftWidth", width.toString())
            }
        }
    }

    fun setRightWidth(width: Int) {
        client.characterId.value?.let { characterId ->
            viewModelScope.launch {
                characterSettingsRepository.save(characterId, "rightWidth", width.toString())
            }
        }
    }

    fun setTopHeight(height: Int) {
        client.characterId.value?.let { characterId ->
            viewModelScope.launch {
                characterSettingsRepository.save(characterId, "topHeight", height.toString())
            }
        }
    }

    fun changeWindowPositions(location: WindowLocation, curPos: Int, newPos: Int) {
        client.characterId.value?.let { characterId ->
            viewModelScope.launch {
                logger.debug { "Swapping $curPos and $newPos" }
                windowRepository.switchPositions(characterId, location, curPos, newPos)
            }
        }
    }

    fun closeWindow(name: String) {
        client.characterId.value?.let { characterId ->
            viewModelScope.launch {
                windowRepository.closeWindow(characterId = characterId, name = name)
            }
        }
    }

    fun saveWindowStyle(name: String, style: StyleDefinition) {
        client.characterId.value?.let { characterId ->
            viewModelScope.launch {
                windowRepository.setStyle(characterId = characterId, name = name, style = style)
            }
        }
    }
}

fun StreamLine.toWindowLine(
    highlights: List<ViewHighlight>,
    presets: Map<String, StyleDefinition>,
    components: Map<String, StyledString>,
    actionHandler: (String) -> Unit,
): WindowLine? {
    val lineStyle = flattenStyles(
        text.getEntireLineStyles(
            variables = components,
            styleMap = presets,
        )
    )
    val annotatedString = buildAnnotatedString {
        lineStyle?.let { pushStyle(it.toSpanStyle()) }
        append(
            text.toAnnotatedString(
                variables = components,
                styleMap = presets,
                actionHandler = actionHandler,
            )
        )
        if (lineStyle != null) pop()
    }
    if (ignoreWhenBlank && annotatedString.isBlank()) {
        return null
    }
    return WindowLine(
        text = annotatedString.highlight(highlights),
        entireLineStyle = lineStyle
    )
}

// Use to track changes in important values in lines
//data class CacheLine(
//    val highlights: ImmutableList<Highlight>,
//    val presets: ImmutableList<PresetStyle>,
//    val components: ImmutableList<Pair<String, StyledString>>,
//    )