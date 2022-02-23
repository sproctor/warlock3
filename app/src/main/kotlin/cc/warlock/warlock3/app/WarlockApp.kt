package cc.warlock.warlock3.app

import androidx.compose.runtime.*
import cc.warlock.warlock3.app.viewmodel.GameViewModel
import cc.warlock.warlock3.app.viewmodel.SgeViewModel
import cc.warlock.warlock3.app.viewmodel.WindowViewModel
import cc.warlock.warlock3.app.views.game.GameView
import cc.warlock.warlock3.app.views.settings.SettingsDialog
import cc.warlock.warlock3.app.views.sge.SgeWizard
import cc.warlock.warlock3.core.prefs.*
import cc.warlock.warlock3.core.prefs.models.GameCharacter
import cc.warlock.warlock3.core.prefs.models.PresetRepository
import cc.warlock.warlock3.core.script.WarlockScriptEngineRegistry
import cc.warlock.warlock3.core.window.WindowRegistry
import cc.warlock.warlock3.stormfront.network.StormfrontClient
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Composable
fun WarlockApp(
    state: MutableState<GameState>,
    clientSettingRepository: ClientSettingRepository,
    accountRepository: AccountRepository,
    characterRepository: CharacterRepository,
    variableRepository: VariableRepository,
    macroRepository: MacroRepository,
    highlightRepository: HighlightRepository,
    presetRepository: PresetRepository,
    windowRegistry: WindowRegistry,
    showSettings: Boolean,
    closeSettings: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    var currentCharacter: GameCharacter? by remember { mutableStateOf(null) }

    when (val currentState = state.value) {
        GameState.NewGameState -> {
            val viewModel = remember {
                SgeViewModel(
                    host = "eaccess.play.net",
                    port = 7900,
                    clientSettingRepository = clientSettingRepository,
                    accountRepository = accountRepository,
                    readyToPlay = { properties, character ->
                        val key = properties["KEY"]!!
                        val host = properties["GAMEHOST"]!!
                        val port = properties["GAMEPORT"]!!.toInt()
                        state.value =
                            GameState.ConnectedGameState(host = host, port = port, key = key, character = character)
                    },
                )
            }
            SgeWizard(viewModel = viewModel)
        }
        is GameState.ConnectedGameState -> {
            val client = remember(currentState.key) {
                currentCharacter = currentState.character
                scope.launch {
                    characterRepository.saveCharacter(currentState.character)
                    clientSettingRepository.put("lastUsername", currentState.character.accountId)
                }
                StormfrontClient(
                    host = currentState.host,
                    port = currentState.port,
                    windowRegistry = windowRegistry,
                    maxTypeAhead = 1,
                ).apply {
                    connect(currentState.key)
                }
            }
            val scriptDirs = clientSettingRepository.observeScriptDirs()
                .stateIn(
                    scope = scope,
                    started = SharingStarted.Eagerly,
                    initialValue = emptyList(),
                )
            val scriptEngineRegistry = remember {
                WarlockScriptEngineRegistry(
                    highlightRepository = highlightRepository,
                    variableRepository = variableRepository,
                    scriptDirectories = scriptDirs,
                )
            }
            val viewModel = remember(client) {
                GameViewModel(
                    client = client,
                    macroRepository = macroRepository,
                    windowRegistry = windowRegistry,
                    variableRepository = variableRepository,
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
                        highlightRepository = highlightRepository,
                        presetRepository = presetRepository,
                    )
                }
            }
            val mainWindowViewModel = remember {
                WindowViewModel(
                    name = "main",
                    client = client,
                    window = windowRegistry.windows.map { it["main"] },
                    highlightRepository = highlightRepository,
                    presetRepository = presetRepository,
                )
            }
            GameView(viewModel = viewModel, windowViewModels.value, mainWindowViewModel)
        }
    }
    if (showSettings) {
        SettingsDialog(
            currentCharacter = currentCharacter,
            closeDialog = closeSettings,
            variableRepository = variableRepository,
            macroRepository = macroRepository,
            presetRepository = presetRepository,
            characterRepository = characterRepository,
            highlightRepository = highlightRepository,
        )
    }
}

@Composable
fun rememberGameState(): MutableState<GameState> {
    return remember { mutableStateOf(GameState.NewGameState) }
}

sealed class GameState {
    object NewGameState : GameState()
    data class ConnectedGameState(val host: String, val port: Int, val key: String, val character: GameCharacter) :
        GameState()
}