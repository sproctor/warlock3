package cc.warlock.warlock3.app

import androidx.compose.runtime.*
import androidx.compose.ui.window.FrameWindowScope
import cc.warlock.warlock3.app.config.ClientSpec
import cc.warlock.warlock3.app.config.SgeSpec
import cc.warlock.warlock3.app.viewmodel.GameViewModel
import cc.warlock.warlock3.app.viewmodel.SgeViewModel
import cc.warlock.warlock3.app.viewmodel.WindowViewModel
import cc.warlock.warlock3.app.views.AppMenuBar
import cc.warlock.warlock3.app.views.game.GameView
import cc.warlock.warlock3.app.views.settings.SettingsDialog
import cc.warlock.warlock3.app.views.sge.SgeWizard
import cc.warlock.warlock3.core.highlights.HighlightRegistry
import cc.warlock.warlock3.core.macros.MacroRepository
import cc.warlock.warlock3.core.script.VariableRegistry
import cc.warlock.warlock3.core.script.WarlockScriptEngineRegistry
import cc.warlock.warlock3.core.text.StyleRepository
import cc.warlock.warlock3.core.util.toCaseInsensitiveMap
import cc.warlock.warlock3.core.window.WindowRegistry
import cc.warlock.warlock3.stormfront.network.StormfrontClient
import com.uchuhimo.konf.Config
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@Composable
fun FrameWindowScope.WarlockApp(
    state: MutableState<GameState>,
    config: StateFlow<Config>,
    saveConfig: ((Config) -> Config) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val windowRegistry = remember { WindowRegistry() }
    var showSettings by remember { mutableStateOf(false) }
    var characterId: String? by remember { mutableStateOf(null) }
    val variableRegistry = remember {
        VariableRegistry(
            variables = config.map { it[ClientSpec.variables] },
            saveVariable = { character: String, name: String, value: String ->
                saveConfig { newConfig ->
                    val allVariables = newConfig[ClientSpec.variables].toCaseInsensitiveMap()
                    val characterVariables = allVariables[character]?.toCaseInsensitiveMap() ?: emptyMap()
                    newConfig[ClientSpec.variables] = allVariables + (character to (characterVariables + (name to value)))
                    newConfig
                }
            },
            deleteVariable = { character, name ->
                saveConfig { newConfig ->
                    val allVariables = newConfig[ClientSpec.variables].toCaseInsensitiveMap()
                    val characterVariables = allVariables.getOrDefault(character, emptyMap()).toCaseInsensitiveMap() - name
                    newConfig[ClientSpec.variables] = allVariables + (character to characterVariables)
                    newConfig
                }
            }
        )
    }
    val highlightRegistry = remember {
        HighlightRegistry(
            globalHighlights = config.map { it[ClientSpec.globalHighlights] }
                .stateIn(scope = scope, started = SharingStarted.Eagerly, initialValue = emptyList()),
            characterHighlights = config.map { it[ClientSpec.characterHighlights] }
                .stateIn(scope = scope, started = SharingStarted.Eagerly, initialValue = emptyMap()),
            addHighlight = { characterId, highlight ->
                saveConfig { config ->
                    if (characterId != null) {
                        val newHighlights =
                            (config[ClientSpec.characterHighlights][characterId] ?: emptyList()) + highlight
                        config[ClientSpec.characterHighlights] =
                            config[ClientSpec.characterHighlights] + (characterId to newHighlights)
                    } else {
                        config[ClientSpec.globalHighlights] = config[ClientSpec.globalHighlights] + highlight
                    }
                    config
                }
            },
            deleteHighlight = { characterId, pattern ->
                saveConfig { config ->
                    if (characterId != null) {
                        val newHighlights =
                            (config[ClientSpec.characterHighlights][characterId]
                                ?: emptyList()).filter { it.pattern != pattern }
                        config[ClientSpec.characterHighlights] =
                            config[ClientSpec.characterHighlights] + (characterId to newHighlights)
                    } else {
                        config[ClientSpec.globalHighlights] =
                            config[ClientSpec.globalHighlights].filter { it.pattern != pattern }
                    }
                    config
                }
            }
        )
    }
    val styleRepository = remember {
        StyleRepository(
            caseSensitiveStyles = config.map { it[ClientSpec.styles] },
            saveStyle = { characterId, name, style ->
                saveConfig { config ->
                    val newStyles = (config[ClientSpec.styles][characterId] ?: emptyMap()) + (name to style)
                    config[ClientSpec.styles] = config[ClientSpec.styles] + (characterId to newStyles)
                    config
                }
            }
        )
    }

    AppMenuBar(
        windowRegistry = windowRegistry,
        showSettings = { showSettings = true },
    )
    when (val currentState = state.value) {
        GameState.NewGameState -> {
            val viewModel = remember {
                SgeViewModel(
                    host = config.value[SgeSpec.host],
                    port = config.value[SgeSpec.port],
                    lastUsername = config.value[SgeSpec.lastUsername],
                    accounts = config.value[ClientSpec.accounts],
                    characters = config.value[ClientSpec.characters],
                    readyToPlay = { properties ->
                        val key = properties["KEY"]
                        val host = properties["GAMEHOST"]
                        val port = properties["GAMEPORT"]?.toInt()
                        state.value = GameState.ConnectedGameState(host = host!!, port = port!!, key = key!!)
                    },
                    saveAccount = { newAccount ->
                        saveConfig { updatedConfig ->
                            updatedConfig[SgeSpec.lastUsername] = newAccount.name
                            updatedConfig[ClientSpec.accounts] =
                                updatedConfig[ClientSpec.accounts].filter { it.name != newAccount.name } + newAccount
                            updatedConfig
                        }
                    }
                )
            }
            SgeWizard(viewModel = viewModel)
        }
        is GameState.ConnectedGameState -> {
            val client = remember(currentState.key) {
                StormfrontClient(
                    host = currentState.host,
                    port = currentState.port,
                    windowRegistry = windowRegistry,
                    maxTypeAhead = config.value[ClientSpec.maxTypeAhead],
                ).apply {
                    connect(currentState.key)
                }
            }
            val clientCharacterId = client.characterId.collectAsState()
            if (characterId != clientCharacterId.value) {
                characterId = clientCharacterId.value
            }
            val macroRepository = remember {
                MacroRepository(
                    globalMacros = config.map { it[ClientSpec.globalMacros] }
                        .stateIn(scope = scope, started = SharingStarted.Eagerly, initialValue = emptyMap()),
                    characterMacros = config.map { it[ClientSpec.characterMacros] }
                        .stateIn(scope = scope, started = SharingStarted.Eagerly, initialValue = emptyMap())
                ).also {
                    println("macros: ${it.characterMacros.value}")
                }
            }
            val scriptEngineRegistry = remember {
                WarlockScriptEngineRegistry(
                    highlightRegistry = highlightRegistry,
                    variableRegistry = variableRegistry,
                )
            }
            val viewModel = remember(client) {
                GameViewModel(
                    client = client,
                    macroRepository = macroRepository,
                    windowRegistry = windowRegistry,
                    variableRegistry = variableRegistry,
                    scriptEngineRegistry = scriptEngineRegistry,
                )
            }
            val windowViewModels = remember { mutableStateOf(emptyMap<String, WindowViewModel>()) }
            val windows by windowRegistry.windows.collectAsState()
            windows.keys.forEach { name ->
                if (name != "main" && windowViewModels.value[name] == null) {
                    windowViewModels.value += name to WindowViewModel(
                        client = client,
                        name = name,
                        window = windowRegistry.windows.map { it[name] },
                        highlightRegistry = highlightRegistry,
                        styleRepository = styleRepository,
                    )
                }
            }
            val mainWindowViewModel = remember {
                WindowViewModel(
                    name = "main",
                    client = client,
                    window = windowRegistry.windows.map { it["main"] },
                    highlightRegistry = highlightRegistry,
                    styleRepository = styleRepository,
                )
            }
            GameView(viewModel = viewModel, windowViewModels.value, mainWindowViewModel)
        }
    }
    if (showSettings) {
        SettingsDialog(
            currentCharacter = characterId,
            config = config,
            closeDialog = {
                showSettings = false
            },
            variableRegistry = variableRegistry,
            styleRepository = styleRepository,
            updateConfig = saveConfig,
        )
    }
}

@Composable
fun rememberGameState(): MutableState<GameState> {
    return remember { mutableStateOf(GameState.NewGameState) }
}

sealed class GameState {
    object NewGameState : GameState()
    data class ConnectedGameState(val host: String, val port: Int, val key: String) : GameState()
}