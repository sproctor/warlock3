package warlockfe.warlock3.compose.ui.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import warlockfe.warlock3.compose.components.CompassState
import warlockfe.warlock3.compose.components.CompassTheme
import warlockfe.warlock3.compose.macros.macroCommands
import warlockfe.warlock3.compose.macros.parseMacroCommand
import warlockfe.warlock3.compose.model.ViewHighlight
import warlockfe.warlock3.compose.ui.window.ComposeTextStream
import warlockfe.warlock3.compose.ui.window.DialogWindowUiState
import warlockfe.warlock3.compose.ui.window.ScrollEvent
import warlockfe.warlock3.compose.ui.window.StreamWindowUiState
import warlockfe.warlock3.compose.ui.window.WindowUiState
import warlockfe.warlock3.compose.util.openUrl
import warlockfe.warlock3.core.client.ClientCompassEvent
import warlockfe.warlock3.core.client.ClientDialogClearEvent
import warlockfe.warlock3.core.client.ClientDialogEvent
import warlockfe.warlock3.core.client.ClientOpenUrlEvent
import warlockfe.warlock3.core.client.DialogObject
import warlockfe.warlock3.core.client.GameCharacter
import warlockfe.warlock3.core.client.SendCommandType
import warlockfe.warlock3.core.client.WarlockAction
import warlockfe.warlock3.core.client.WarlockClient
import warlockfe.warlock3.core.macro.MacroKeyCombo
import warlockfe.warlock3.core.macro.MacroToken
import warlockfe.warlock3.core.prefs.repositories.AliasRepository
import warlockfe.warlock3.core.prefs.repositories.AlterationRepository
import warlockfe.warlock3.core.prefs.repositories.CharacterSettingsRepository
import warlockfe.warlock3.core.prefs.repositories.HighlightRepositoryImpl
import warlockfe.warlock3.core.prefs.repositories.MacroRepository
import warlockfe.warlock3.core.prefs.repositories.NameRepositoryImpl
import warlockfe.warlock3.core.prefs.repositories.PresetRepository
import warlockfe.warlock3.core.prefs.repositories.VariableRepository
import warlockfe.warlock3.core.prefs.repositories.WindowRepository
import warlockfe.warlock3.core.prefs.repositories.defaultMaxTypeAhead
import warlockfe.warlock3.core.prefs.repositories.defaultStyles
import warlockfe.warlock3.core.prefs.repositories.maxTypeAheadKey
import warlockfe.warlock3.core.script.ScriptManager
import warlockfe.warlock3.core.script.ScriptStatus
import warlockfe.warlock3.core.text.Alias
import warlockfe.warlock3.core.text.StyleDefinition
import warlockfe.warlock3.core.text.StyledString
import warlockfe.warlock3.core.text.WarlockStyle
import warlockfe.warlock3.core.util.splitFirstWord
import warlockfe.warlock3.core.window.StreamRegistry
import warlockfe.warlock3.core.window.Window
import warlockfe.warlock3.core.window.WindowLocation
import warlockfe.warlock3.core.window.WindowType
import warlockfe.warlock3.wrayth.network.clientCommandPrefix
import warlockfe.warlock3.wrayth.network.scriptCommandPrefix
import warlockfe.warlock3.wrayth.util.CompiledAlteration
import java.io.File
import kotlin.math.max

@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModel(
    val windowRepository: WindowRepository,
    private val client: WarlockClient,
    val macroRepository: MacroRepository,
    val variableRepository: VariableRepository,
    highlightRepository: HighlightRepositoryImpl,
    nameRepository: NameRepositoryImpl,
    presetRepository: PresetRepository,
    private val scriptManager: ScriptManager,
    val compassTheme: CompassTheme,
    val characterSettingsRepository: CharacterSettingsRepository,
    private val alterationRepository: AlterationRepository,
    aliasRepository: AliasRepository,
    private val streamRegistry: StreamRegistry,
    private val mainDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val logger = KotlinLogging.logger { }

    var entryText by mutableStateOf(TextFieldValue())
        private set

    private val _scrollEvents = MutableStateFlow<PersistentList<ScrollEvent>>(persistentListOf())
    val scrollEvents = _scrollEvents.asStateFlow()

    private val _compassState = MutableStateFlow(CompassState(emptySet()))
    val compassState: StateFlow<CompassState> = _compassState

    private val _dialogs = MutableStateFlow<Map<String, List<DialogObject>>>(emptyMap())

    val vitalBars: Flow<List<DialogObject>> = _dialogs.map { dialogs ->
        dialogs["minivitals"] ?: emptyList()
    }

    // Saved by macros
    private var storedText: String? = null

    val properties: StateFlow<Map<String, String>> = client.properties

    val characterId = client.characterId

    val character = combine(characterId, properties) { characterId, properties ->
        val game = properties["game"]
        val name = properties["character"]
        if (characterId != null && game != null && name != null) {
            GameCharacter(id = characterId, gameCode = game, name = name)
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

    @OptIn(ExperimentalCoroutinesApi::class)
    private val alterations: StateFlow<List<CompiledAlteration>> = characterId.flatMapLatest { characterId ->
        if (characterId != null)
            alterationRepository.observeForCharacter(characterId).map { list ->
                list.mapNotNull {
                    try {
                        CompiledAlteration(it)
                    } catch (_: Exception) {
                        null
                    }
                }
            }
        else
            flow { }
    }
        .stateIn(scope = viewModelScope, started = SharingStarted.Eagerly, initialValue = emptyList())

    private val runningScripts =
        scriptManager.runningScripts.stateIn(viewModelScope, SharingStarted.Eagerly, persistentMapOf())

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

    private var historyPosition = 0
    private val sendHistory = mutableListOf("")

    private val highlights: Flow<List<ViewHighlight>> = client.characterId.flatMapLatest { characterId ->
        if (characterId != null) {
            combine(
                highlightRepository.observeForCharacter(characterId),
                nameRepository.observeForCharacter(characterId)
            ) { highlights, names ->
                val generalHighlights = highlights.mapNotNull { highlight ->
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
                            styles = highlight.styles
                        )
                    } catch (e: Exception) {
                        client.debug("Error while parsing highlight (${e.message}): $highlight")
                        null
                    }
                }
                val nameHighlights = names.mapNotNull { name ->
                    val pattern = Regex.escape(name.text).let { "\\b$it\\b" }
                    try {
                        ViewHighlight(
                            regex = Regex(pattern = pattern),
                            styles = mapOf(
                                0 to StyleDefinition(
                                    textColor = name.textColor,
                                    backgroundColor = name.backgroundColor,
                                )
                            )
                        )
                    }  catch (e: Exception) {
                        client.debug("Error while parsing highlight (${e.message}): $name")
                        null
                    }
                }
                generalHighlights + nameHighlights
            }
        } else {
            flow {
                emit(emptyList())
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    val windowUiStates: StateFlow<List<WindowUiState>> =
        combine(
            windowRepository.openWindows,
            windowRepository.windows,
            presets,
            highlights,
            alterations,
            _dialogs,
        ) { flows ->
            val openWindows = flows[0] as Set<String>
            val windows = flows[1] as Map<String, Window>
            val presets = flows[2] as Map<String, StyleDefinition>
            val highlights = flows[3] as List<ViewHighlight>
            val alterations = flows[4] as List<CompiledAlteration>
            val dialogs = flows[5] as Map<String, List<DialogObject>>

            openWindows.map { name ->
                val window = windows[name]
                if (window?.windowType == WindowType.DIALOG) {
                    DialogWindowUiState(
                        name = name,
                        window = window,
                        dialogData = dialogs[name] ?: emptyList(),
                        style = presets["default"] ?: defaultStyles["default"]!!,
                        width = null,
                        height = null,
                    )
                } else {
                    StreamWindowUiState(
                        name = name,
                        stream = streamRegistry.getOrCreateStream(name) as ComposeTextStream,
                        window = window,
                        highlights = highlights,
                        presets = presets,
                        alterations = alterations.filter { it.appliesToStream(name) },
                        defaultStyle = presets["default"] ?: defaultStyles["default"]!!,
                    )
                }
            }
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = emptyList(),
            )

    val mainWindowUiState: StateFlow<WindowUiState> =
        combine(
            windowRepository.windows,
            presets,
            highlights,
            alterations
        ) { windows, presets, highlights, alterations ->
            val name = "main"
            StreamWindowUiState(
                name = name,
                stream = streamRegistry.getOrCreateStream(name) as ComposeTextStream,
                window = windows[name],
                highlights = highlights,
                presets = presets,
                alterations = alterations.filter { it.appliesToStream(name) },
                defaultStyle = presets["default"] ?: defaultStyles["default"]!!,
            )
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue =
                    StreamWindowUiState(
                        name = "main",
                        stream = streamRegistry.getOrCreateStream("main") as ComposeTextStream,
                        window = null,
                        highlights = emptyList(),
                        presets = emptyMap(),
                        alterations = emptyList(),
                        defaultStyle = defaultStyles["default"]!!,
                    )
            )

    private val _selectedWindow: MutableStateFlow<String> = MutableStateFlow(mainWindowUiState.value.name)
    val selectedWindow: StateFlow<String> = _selectedWindow

    val disconnected = client.disconnected

    val menuData = client.menuData

    init {
        client.eventFlow
            .onEach { event ->
                when (event) {
                    is ClientDialogEvent -> {
                        _dialogs.update { origDialogs ->
                            val data = origDialogs[event.id]?.toMutableList() ?: mutableListOf()
                            val existingIndex = data.indexOfFirst {
                                it.id.equals(other = event.data.id, ignoreCase = true)
                            }
                            if (existingIndex != -1) {
                                data[existingIndex] = event.data
                            } else {
                                data.add(event.data)
                            }
                            val dialogs = origDialogs.toMutableMap()
                            dialogs[event.id] = data
                            dialogs.toPersistentMap()
                        }
                    }

                    is ClientDialogClearEvent -> {
                        _dialogs.update { origDialogs ->
                            val newDialogs = origDialogs.toMutableMap()
                            newDialogs[event.id] = emptyList()
                            newDialogs.toPersistentMap()
                        }
                    }

                    is ClientCompassEvent -> {
                        _compassState.value = CompassState(directions = event.directions.toSet())
                    }

                    is ClientOpenUrlEvent -> {
                        openUrl(event.url)
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

        val scriptStream = client.getStream("warlockscripts")
        runningScripts
            .onEach { scripts ->
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
        var line = entryText.text
        entryText = TextFieldValue()
        aliases.value.forEach { alias ->
            line = alias.replace(line)
        }
        if (sendHistory.getOrNull(1) != line) {
            sendHistory[0] = line
            sendHistory.add(0, "")
        }
        historyPosition = 0
        sendCommand(line)
    }

    fun sendCommand(command: String) {
        viewModelScope.launch {
            commandHandler(command)
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

    fun runScript(file: File) {
        viewModelScope.launch(Dispatchers.IO) {
            scriptManager.startScript(client, file, ::commandHandler)
        }
    }

    fun handleKeyPress(event: KeyEvent, clipboard: Clipboard): Boolean {
        if (event.type != KeyEventType.KeyDown) {
            return false
        }

        val keyString = translateKeyPress(event)
        val macroString = macros.value[keyString]

        if (macroString != null) {
            val tokens = parseMacroCommand(macroString)

            if (tokens == null) {
                viewModelScope.launch {
                    client.print(StyledString("Invalid macro: $macroString"))
                }
                return false
            }

            executeMacro(tokens, clipboard)

            return true
        }

        if (event.key.keyCode == Key.Enter.keyCode) {
            submit()
            return true
        }

        return false
    }

    private fun executeMacro(tokens: List<MacroToken>, clipboard: Clipboard) {
        viewModelScope.launch(mainDispatcher) {
            var movedCursor = false
            tokens.forEach { token ->
                when (token) {
                    is MacroToken.Entity -> {
                        handleEntity(token.char, movedCursor)
                    }

                    MacroToken.At -> {
                        entryText = entryText.copy(selection = TextRange(entryText.text.length))
                        movedCursor = true
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

    private suspend fun handleEntity(entity: Char, movedCursor: Boolean) {
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

            '?' -> {
                storedText?.let { entryAppend(it, !movedCursor) }
            }
        }
    }

    // Must be called from main thread
    private fun entryClear() {
        entryText = TextFieldValue()
    }

    // Must be called from main thread
    private fun entryAppend(text: String, moveCursor: Boolean) {
        val newText = entryText.text + text
        val selection = if (moveCursor) {
            TextRange(newText.length)
        } else {
            entryText.selection
        }
        entryText = entryText.copy(text = newText, selection = selection)
    }

    // Must be called from main thread
    fun entryDelete(range: TextRange) {
        val newText = entryText.text.removeRange(range.start, range.end)
        val selection = entryText.selection
        val newSelection = if (range.start < selection.start) {
            TextRange(
                start = selection.start - range.length,
                end = selection.end - range.length,
            )
        } else {
            selection
        }
        entryText = entryText.copy(text = newText, selection = newSelection)
    }

    // Must be called from main thread
    fun entrySetSelection(selection: TextRange) {
        entryText = entryText.copy(selection = selection)
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
            sendHistory[historyPosition] = entryText.text
            historyPosition++
            val text = history[historyPosition]
            entryText = TextFieldValue(text = text, selection = TextRange(text.length))
        }
    }

    fun historyNext() {
        if (historyPosition > 0) {
            sendHistory[historyPosition] = entryText.text
            historyPosition--
            val text = sendHistory[historyPosition]
            entryText = TextFieldValue(text = text, selection = TextRange(text.length))
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

    fun updateEntryText(value: TextFieldValue) {
        entryText = value
    }

    fun moveWindow(name: String, location: WindowLocation) {
        viewModelScope.launch {
            windowRepository.moveWindow(
                name = name,
                location = location
            )
        }
    }

    fun setWindowWidth(name: String, width: Int) {
        viewModelScope.launch {
            windowRepository.setWindowWidth(
                name = name,
                width = width
            )
        }
    }

    fun setWindowHeight(name: String, height: Int) {
        viewModelScope.launch {
            windowRepository.setWindowHeight(
                name = name,
                height = height
            )
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

    fun changeWindowPositions(location: WindowLocation, curPos: Int, newPos: Int) {
        viewModelScope.launch {
            logger.debug { "Swapping $curPos and $newPos" }
            windowRepository.switchPositions(location, curPos, newPos)
        }
    }

    fun closeWindow(name: String) {
        viewModelScope.launch {
            windowRepository.closeWindow(name = name)
        }
    }

    fun clearStream(name: String) {
        viewModelScope.launch {
            val stream = streamRegistry.getOrCreateStream(name)
            stream.clear()
        }
    }

    fun saveWindowStyle(name: String, style: StyleDefinition) {
        viewModelScope.launch {
            windowRepository.setStyle(name = name, style = style)
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
        return if (line.startsWith(scriptCommandPrefix)) {
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
}
