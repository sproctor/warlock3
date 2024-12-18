package warlockfe.warlock3.compose.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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
) : ViewModel() {

    val characters = characterRepository.observeAllCharacters()

    private val logger = KotlinLogging.logger { }

    private var busy = false

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    // TODO: Make these configurable?
    private val host = "eaccess.play.net"
    private val port = 7900

    private var connectJob: Job? = null

    fun connectCharacter(character: GameCharacter) {
        // TODO: display message when connecting character
        if (busy) return
        busy = true
        connectJob?.cancel()
        connectJob = viewModelScope.launch(ioDispatcher) {
            try {
                _message.value = "Connecting..."
                val client = sgeClientFactory.create(host = host, port = port)
                val result = client.connect()
                if (result.isFailure) {
                    logger.error(result.exceptionOrNull()) { "Unable to connect to server" }
                    _message.value = "Could not connect to SGE"
                    return@launch
                }
                viewModelScope.launch(Dispatchers.IO) {
                    client.eventFlow
                        .collect { event ->
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
                                        val gameViewModel = gameViewModelFactory.create(
                                            sfClient,
                                            gameState.windowRepository,
                                            gameState.streamRegistry
                                        )
                                        gameState.screen = GameScreen.ConnectedGameState(gameViewModel)
                                    } catch (e: UnknownHostException) {
                                        gameState.screen = GameScreen.ErrorState("Unknown host: ${e.message}")
                                    } catch (e: Exception) {
                                        logger.error(e) { "Error connecting to server" }
                                        gameState.screen = GameScreen.ErrorState("Error: ${e.message}")
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
                }
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