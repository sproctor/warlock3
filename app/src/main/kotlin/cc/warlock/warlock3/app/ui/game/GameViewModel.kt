package cc.warlock.warlock3.app.ui.game

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import cc.warlock.warlock3.app.components.CompassState
import cc.warlock.warlock3.app.components.CompassTheme
import cc.warlock.warlock3.app.macros.macroCommands
import cc.warlock.warlock3.app.model.ViewHighlight
import cc.warlock.warlock3.app.ui.window.WindowUiState
import cc.warlock.warlock3.app.util.toSpanStyle
import cc.warlock.warlock3.core.client.ClientCompassEvent
import cc.warlock.warlock3.core.client.ClientProgressBarEvent
import cc.warlock.warlock3.core.client.ProgressBarData
import cc.warlock.warlock3.core.parser.MacroLexer
import cc.warlock.warlock3.core.prefs.*
import cc.warlock.warlock3.core.client.GameCharacter
import cc.warlock.warlock3.core.script.WarlockScriptEngineRegistry
import cc.warlock.warlock3.core.text.StyleDefinition
import cc.warlock.warlock3.core.text.StyledString
import cc.warlock.warlock3.core.text.WarlockStyle
import cc.warlock.warlock3.core.window.WindowLocation
import cc.warlock.warlock3.stormfront.network.StormfrontClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.Token
import kotlin.math.max

@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModel(
    private val windowRepository: WindowRepository,
    val client: StormfrontClient,
    val macroRepository: MacroRepository,
    val variableRepository: VariableRepository,
    highlightRepository: HighlightRepository,
    presetRepository: PresetRepository,
    private val scriptEngineRegistry: WarlockScriptEngineRegistry,
    val compassTheme: CompassTheme,
    val clipboard: ClipboardManager,
    private val characterSettingsRepository: CharacterSettingsRepository,
) : AutoCloseable {
    private val viewModelScope = CoroutineScope(Dispatchers.Default)

    private val _entryText = mutableStateOf(TextFieldValue())
    val entryText: State<TextFieldValue> = _entryText

    private val _compassState = mutableStateOf(CompassState(emptySet()))
    val compassState: State<CompassState> = _compassState

    private val _vitalBars = mutableStateMapOf<String, ProgressBarData>()
    val vitalBars: SnapshotStateMap<String, ProgressBarData> = _vitalBars

    // Saved by macros
    private val storedText = mutableStateOf<String?>(null)

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
            .map { it.toMap() }
    }
        .stateIn(scope = viewModelScope, started = SharingStarted.Eagerly, initialValue = emptyMap())

    private val presets: Flow<Map<String, StyleDefinition>> = client.characterId.flatMapLatest { characterId ->
        if (characterId != null) {
            presetRepository.observePresetsForCharacter(characterId)
        } else {
            flow { emit(emptyMap()) }
        }
    }

    val variables: StateFlow<Map<String, String>> = client.characterId.flatMapLatest { characterId ->
        if (characterId != null) {
            variableRepository.observeCharacterVariables(characterId).map { list ->
                list.associate { it.name to it.value }
            }
        } else {
            flow<Map<String, String>> { emit(emptyMap()) }
        }
    }
        .stateIn(scope = viewModelScope, started = SharingStarted.Eagerly, initialValue = emptyMap())

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

    private val highlights = client.characterId.flatMapLatest { characterId ->
        if (characterId != null) {
            highlightRepository.observeForCharacter(characterId)
                .map { highlights ->
                    highlights.map { highlight ->
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
                        ViewHighlight(
                            regex = Regex(
                                pattern = pattern,
                                options = if (highlight.ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet(),
                            ),
                            styles = highlight.styles.mapValues { it.value.toSpanStyle() }
                        )
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
        .stateIn(scope = viewModelScope, started = SharingStarted.Eagerly, initialValue = emptySet())

    val windowUiStates: Flow<List<WindowUiState>> = combine(
        highlights, presets, openWindows, windows, client.components
    ) { highlights, presets, openWindows, windows, components ->
        openWindows.map { name ->
            WindowUiState(
                name = name,
                lines = client.getStream(name).lines,
                window = windows[name],
                components = components,
                highlights = highlights,
                presets = presets,
            )
        }
    }

    val mainWindowUiState: Flow<WindowUiState> = combine(
        highlights, presets, windows, client.components
    ) { highlights, presets, windows, components ->
        val name = "main"
        WindowUiState(
            name = name,
            lines = client.getStream(name).lines,
            window = windows[name],
            components = components,
            highlights = highlights,
            presets = presets,
        )
    }

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
    }

    fun submit() {
        val line = _entryText.value.text
        _entryText.value = TextFieldValue()
        sendHistory.add(0, line)
        historyPosition = -1
        sendCommand(line)
    }

    fun sendCommand(command: String) {
        viewModelScope.launch {
            if (command.startsWith(".")) {
                val scriptCommand = command.drop(1)
                scriptEngineRegistry.startScript(client, scriptCommand)
                client.print(StyledString(command, WarlockStyle.Command))
            } else {
                client.sendCommand(command)
            }
        }
    }

    suspend fun stopScripts() {
        val scriptInstances = scriptEngineRegistry.runningScripts.value
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
        val scriptInstances = scriptEngineRegistry.runningScripts.value
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

    @OptIn(ExperimentalComposeUiApi::class)
    fun handleKeyPress(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) {
            return false
        }
        if (event.key.keyCode == Key.Enter.keyCode) {
            submit()
            return true
        }

        val keyString = translateKeyPress(event)
        val macroString = macros.value[keyString] ?: return false

        val tokens = try {
            tokenizeMacro(macroString)
        } catch (e: Exception) {
            return false
        }
        executeMacro(tokens)

        return true
    }

    private fun executeMacro(tokens: List<Token>) {
        viewModelScope.launch {
            var movedCursor = false
            tokens.forEach { token ->
                when (token.type) {
                    MacroLexer.Entity -> {
                        val entity = token.text
                        assert(entity.length == 2)
                        assert(entity[0] == '\\')
                        handleEntity(entity[1])
                    }
                    MacroLexer.At -> {
                        _entryText.value =
                            _entryText.value.copy(selection = TextRange(_entryText.value.text.length))
                        movedCursor = true
                    }
                    MacroLexer.Question -> {
                        storedText.value?.let { entryAppend(it, !movedCursor) }
                    }
                    MacroLexer.Character -> {
                        entryAppend(token.text, !movedCursor)
                    }
                    MacroLexer.VariableName -> {
                        token.text?.let { if (it.endsWith("%")) it.drop(1) else it }
                            ?.let { name ->
                                entryAppend(variables.value[name] ?: "", !movedCursor)
                            }
                    }
                    MacroLexer.CommandText -> {
                        val command = macroCommands[token.text.lowercase()]
                        command?.invoke(this@GameViewModel)
                    }
                }
            }
        }
    }

    private suspend fun handleEntity(entity: Char) {
        when (entity) {
            'x' -> {
                storedText.value = _entryText.value.text
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
        _entryText.value = TextFieldValue()
    }

    private fun entryAppend(text: String, moveCursor: Boolean) {
        val newText = _entryText.value.text + text
        val selection = if (moveCursor) {
            TextRange(newText.length)
        } else {
            _entryText.value.selection
        }
        _entryText.value = _entryText.value.copy(text = newText, selection = selection)
    }

    fun entryInsert(text: String) {
        val currentTextField = _entryText.value
        val prefix = currentTextField.text.substring(0, currentTextField.selection.start)
        val postfix = currentTextField.text.substring(currentTextField.selection.end, currentTextField.text.length)
        val newText = prefix + text + postfix
        val pos = prefix.length + text.length
        _entryText.value = currentTextField.copy(text = newText, selection = TextRange(pos))
    }

    fun historyPrev() {
        val history = sendHistory
        if (historyPosition < history.size - 1) {
            historyPosition++
            val text = history[historyPosition]
            _entryText.value = TextFieldValue(text = text, selection = TextRange(text.length))
        }
    }

    fun historyNext() {
        if (historyPosition >= 0) {
            historyPosition--
            if (historyPosition < 0) {
                entryClear()
            } else {
                val text = sendHistory[historyPosition]
                _entryText.value = TextFieldValue(text = text, selection = TextRange(text.length))
            }
        }
    }

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
        keyString.append(event.key.keyCode)
        return keyString.toString()
    }

    private fun tokenizeMacro(input: String): List<Token> {
        val charStream = CharStreams.fromString(input)
        val lexer = MacroLexer(charStream)
        return lexer.allTokens
    }

    fun setEntryText(value: TextFieldValue) {
        _entryText.value = value
    }

    fun moveWindow(name: String, location: WindowLocation) {
        client.characterId.value?.let { characterId ->
            viewModelScope.launch {
                windowRepository.moveWindow(characterId = characterId, name = name, location = location)
            }
        }
    }

    fun setWindowWidth(name: String, width: Int) {
        client.characterId.value?.let { characterId ->
            viewModelScope.launch {
                windowRepository.setWindowWidth(characterId = characterId, name = name, width = width)
            }
        }
    }

    fun setWindowHeight(name: String, height: Int) {
        client.characterId.value?.let { characterId ->
            viewModelScope.launch {
                windowRepository.setWindowHeight(characterId = characterId, name = name, height = height)
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
                println("Swapping $curPos and $newPos")
                windowRepository.switchPositions(characterId, location, curPos, newPos)
            }
        }
    }

    override fun close() {
        viewModelScope.cancel()
    }
}
