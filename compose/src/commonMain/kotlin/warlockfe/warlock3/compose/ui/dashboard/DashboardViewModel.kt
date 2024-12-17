package warlockfe.warlock3.compose.ui.dashboard

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import warlockfe.warlock3.compose.model.GameScreen
import warlockfe.warlock3.compose.model.GameState
import warlockfe.warlock3.compose.ui.game.GameViewModelFactory
import warlockfe.warlock3.core.client.GameCharacter
import warlockfe.warlock3.core.client.WarlockClientFactory
import warlockfe.warlock3.core.prefs.AccountRepository
import warlockfe.warlock3.core.prefs.CharacterRepository
import warlockfe.warlock3.core.sge.SgeClientFactory
import warlockfe.warlock3.core.sge.SgeEvent
import java.net.UnknownHostException

class DashboardViewModel(
    characterRepository: CharacterRepository,
    private val accountRepository: AccountRepository,
    private val gameState: GameState,
    private val sgeClientFactory: SgeClientFactory,
    private val warlockClientFactory: WarlockClientFactory,
    private val gameViewModelFactory: GameViewModelFactory,
    private val ioDispatcher: CoroutineDispatcher,
) {
    val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
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
        connectJob = scope.launch(ioDispatcher) {
            try {
                _message.value = "Connecting..."
                val client = sgeClientFactory.create(host = host, port = port)
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
                                    val sfClient = warlockClientFactory.createStormFrontClient(
                                        credentials,
                                        gameState.windowRepository,
                                        gameState.streamRegistry,
                                    )
                                    sfClient.connect()
                                    val gameViewModel = gameViewModelFactory.create(sfClient, gameState.windowRepository, gameState.streamRegistry)
                                    gameState.screen = GameScreen.ConnectedGameState(gameViewModel)
                                } catch (e: UnknownHostException) {
                                    gameState.screen = GameScreen.ErrorState("Unknown host: ${e.message}")
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
                client.login(username = account.username, account.password ?: "")
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