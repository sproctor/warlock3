package warlockfe.warlock3.compose.ui.sge

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import warlockfe.warlock3.compose.model.GameScreen
import warlockfe.warlock3.compose.model.GameState
import warlockfe.warlock3.compose.ui.game.GameViewModelFactory
import warlockfe.warlock3.core.client.GameCharacter
import warlockfe.warlock3.core.client.WarlockClientFactory
import warlockfe.warlock3.core.prefs.AccountRepository
import warlockfe.warlock3.core.prefs.CharacterRepository
import warlockfe.warlock3.core.prefs.ClientSettingRepository
import warlockfe.warlock3.core.prefs.models.AccountEntity
import warlockfe.warlock3.core.sge.SgeCharacter
import warlockfe.warlock3.core.sge.SgeClientFactory
import warlockfe.warlock3.core.sge.SgeError
import warlockfe.warlock3.core.sge.SgeEvent
import warlockfe.warlock3.core.sge.SgeGame
import java.net.UnknownHostException

// FIXME: This needs some re-organization
class SgeViewModel(
    private val clientSettingRepository: ClientSettingRepository,
    private val accountRepository: AccountRepository,
    private val characterRepository: CharacterRepository,
    private val warlockClientFactory: WarlockClientFactory,
    sgeClientFactory: SgeClientFactory,
    private val gameViewModelFactory: GameViewModelFactory,
    private val gameState: GameState,
) : ViewModel() {

    private val logger = KotlinLogging.logger { }

    // TODO: Make these configurable?
    private val host = "eaccess.play.net"
    private val port = 7900

    private val _state = mutableStateOf<SgeViewState>(SgeViewState.SgeConnecting)
    val state: State<SgeViewState> = _state
    private val backStack = mutableStateListOf<SgeViewState>()

    private val client = sgeClientFactory.create(host = host, port = port)
    private val job: Job

    private var accountId: String? = null
    private var characterName: String? = null
    private var gameCode: String? = null

    val lastAccount: Flow<AccountEntity?> = flow {
        emit(
            clientSettingRepository.get("lastUsername")?.let { username ->
                accountRepository.getByUsername(username)
            }
        )
    }

    init {
        job = viewModelScope.launch {
            if (client.connect().isFailure) {
                logger.debug { "Failed to connect to server" }
                _state.value = SgeViewState.SgeError("Failed to connect to server")
                return@launch
            }
            navigate(SgeViewState.SgeAccountSelector)
            client.eventFlow.collect { event ->
                logger.debug { "Got event: $event" }
                when (event) {
                    SgeEvent.SgeLoginSucceededEvent -> client.requestGameList()
                    is SgeEvent.SgeGamesReadyEvent -> navigate(SgeViewState.SgeGameSelector(event.games))
                    is SgeEvent.SgeGameSelectedEvent -> client.requestCharacterList()
                    is SgeEvent.SgeCharactersReadyEvent -> {
                        val currentState = _state.value
                        if (currentState is SgeViewState.SgeLoadingCharacterList) {
                            navigate(
                                SgeViewState.SgeCharacterSelector(
                                    currentState.game,
                                    event.characters
                                )
                            )
                        } else {
                            logger.debug { "Got character list in unexpected state" }
                        }
                    }
                    is SgeEvent.SgeErrorEvent -> {
                        val errorMessage = when (event.errorCode) {
                            SgeError.INVALID_PASSWORD -> "Invalid password"
                            SgeError.INVALID_ACCOUNT -> "Invalid username"
                            SgeError.ACCOUNT_REJECTED -> "Account is not active"
                            SgeError.ACCOUNT_EXPIRED -> "Account payment has expired"
                            SgeError.UNKNOWN_ERROR -> "An unknown error has occurred"
                        }
                        navigate(SgeViewState.SgeError(errorMessage))
                    }
                    is SgeEvent.SgeReadyToPlayEvent -> {
                        val credentials = event.credentials
                        val character =
                            GameCharacter(
                                accountId,
                                "$gameCode:$characterName".lowercase(),
                                gameCode!!,
                                characterName!!
                            )

                        viewModelScope.launch {
                            characterRepository.saveCharacter(character)
                            clientSettingRepository.putLastUsername(accountId)
                        }

                        try {
                            val sfClient = warlockClientFactory.createStormFrontClient(credentials, gameState.windowRepository, gameState.streamRegistry)
                            sfClient.connect()
                            val gameViewModel = gameViewModelFactory.create(sfClient, gameState.windowRepository, gameState.streamRegistry)
                            gameState.screen = GameScreen.ConnectedGameState(gameViewModel)
                        } catch (e: UnknownHostException) {
                            gameState.screen = GameScreen.ErrorState("Unknown host: ${e.message}", returnTo = GameScreen.NewGameState)
                        }
                    }
                }
            }
        }
    }

    fun accountSelected(account: AccountEntity) {
        _state.value = SgeViewState.SgeLoadingGameList
        accountId = account.username
        viewModelScope.launch {
            client.login(account.username, account.password ?: "")
        }
    }

    fun gameSelected(game: SgeGame) {
        _state.value = SgeViewState.SgeLoadingCharacterList(game)
        gameCode = game.code
        viewModelScope.launch {
            client.selectGame(game.code)
        }
    }

    fun characterSelected(game: SgeGame, character: SgeCharacter) {
        _state.value = SgeViewState.SgeConnectingToGame(game, character)
        characterName = character.name
        viewModelScope.launch {
            client.selectCharacter(character.code)
        }
    }

    fun goBack() {
        backStack.removeLast()
        _state.value = backStack.last()
    }

    fun saveAccount(account: AccountEntity) {
        logger.debug { "Saving account" }
        viewModelScope.launch {
            accountRepository.save(account)
        }
    }

    private fun navigate(newState: SgeViewState) {
        backStack += listOf(newState)
        _state.value = newState
    }

    override fun onCleared() {
        super.onCleared()
        client.close()
        job.cancel()
    }
}

sealed interface SgeViewState {
    data object SgeConnecting : SgeViewState
    data object SgeAccountSelector : SgeViewState
    data class SgeGameSelector(val games: List<SgeGame>) : SgeViewState
    data class SgeCharacterSelector(val game: SgeGame, val characters: List<SgeCharacter>) : SgeViewState
    data object SgeLoadingGameList : SgeViewState
    data class SgeLoadingCharacterList(val game: SgeGame) : SgeViewState
    data class SgeConnectingToGame(val game: SgeGame, val character: SgeCharacter) : SgeViewState
    data class SgeError(val error: String) : SgeViewState
}