package cc.warlock.warlock3.app.ui.dashboard

import androidx.compose.ui.platform.ClipboardManager
import cc.warlock.warlock3.app.GameState
import cc.warlock.warlock3.app.di.AppContainer
import cc.warlock.warlock3.core.prefs.AccountRepository
import cc.warlock.warlock3.core.prefs.CharacterRepository
import cc.warlock.warlock3.core.client.GameCharacter
import cc.warlock.warlock3.stormfront.network.SgeClient
import cc.warlock.warlock3.stormfront.network.SgeEvent
import cc.warlock.warlock3.stormfront.network.StormfrontClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.net.UnknownHostException

class DashboardViewModel(
    characterRepository: CharacterRepository,
    private val accountRepository: AccountRepository,
    private val updateGameState: (GameState) -> Unit,
    private val clipboardManager: ClipboardManager,
) {
    val characters = characterRepository.observeAllCharacters()

    var busy = false

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    // TODO: Make these configurable?
    private val host = "eaccess.play.net"
    private val port = 7900

    private var connectJob: Job? = null

    @OptIn(DelicateCoroutinesApi::class)
    fun connectCharacter(character: GameCharacter) {
        if (busy) return
        busy = true
        connectJob?.cancel()
        connectJob = GlobalScope.launch {
            try {
                _message.value = "Connecting..."
                val client = SgeClient(host = host, port = port)
                val result = client.connect()
                if (result.isFailure) {
                    _message.value = "Could not connect to SGE"
                    return@launch
                }
                client.eventFlow
                    .onEach { event ->
                        when (event) {
                            is SgeEvent.SgeLoginSucceededEvent -> client.selectGame(character.gameCode)
                            is SgeEvent.SgeGameSelectedEvent -> client.requestCharacterList()
                            is SgeEvent.SgeCharactersReadyEvent -> {
                                val characters = event.characters
                                val sgeCharacter = characters.firstOrNull { it.name.equals(character.name, true) }
                                if (sgeCharacter == null) {
                                    _message.value = "Could not find character: ${character.name}"
                                } else {
                                    client.selectCharacter(sgeCharacter.code)
                                }
                            }
                            is SgeEvent.SgeReadyToPlayEvent -> {
                                val credentials = event.credentials
                                try {
                                    val sfClient = StormfrontClient(
                                        host = credentials.host,
                                        port = credentials.port,
                                        windowRepository = AppContainer.windowRepository,
                                        characterRepository = AppContainer.characterRepository,
                                        characterSettingsRepository = AppContainer.characterSettingsRepository,
                                        scriptEngineRegistry = AppContainer.scriptEngineRegistry,
                                    )
                                    sfClient.connect(credentials.key)
                                    val gameViewModel = AppContainer.gameViewModelFactory(sfClient, clipboardManager)
                                    updateGameState(
                                        GameState.ConnectedGameState(gameViewModel)
                                    )
                                } catch (e: UnknownHostException) {
                                    updateGameState(GameState.ErrorState("Unknown host: ${e.message}"))
                                }
                                client.close()
                                cancelConnect()
                            }
                            is SgeEvent.SgeErrorEvent -> {
                                _message.value = "Error code (${event.errorCode})"
                                connectJob?.cancel()
                            }
                            else -> Unit // we don't care?
                        }
                    }
                    .launchIn(GlobalScope)
                val account = character.accountId?.let { accountRepository.getByUsername(it) }
                if (account == null) {
                    _message.value = "Invalid account"
                    return@launch
                }
                client.login(username = account.username, account.password)
            } finally {
                connectJob = null
                busy = false
            }
        }
    }

    fun cancelConnect() {
        connectJob?.cancel()
        connectJob = null
    }
}