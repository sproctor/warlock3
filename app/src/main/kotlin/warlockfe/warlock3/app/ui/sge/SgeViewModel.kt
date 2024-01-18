package warlockfe.warlock3.app.ui.sge

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ClipboardManager
import warlockfe.warlock3.app.GameState
import warlockfe.warlock3.app.di.AppContainer
import warlockfe.warlock3.core.prefs.AccountRepository
import warlockfe.warlock3.core.prefs.ClientSettingRepository
import warlockfe.warlock3.core.prefs.models.Account
import warlockfe.warlock3.core.client.GameCharacter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import warlockfe.warlock3.stormfront.network.SgeCharacter
import warlockfe.warlock3.stormfront.network.SgeClient
import warlockfe.warlock3.stormfront.network.SgeError
import warlockfe.warlock3.stormfront.network.SgeEvent
import warlockfe.warlock3.stormfront.network.SgeGame
import warlockfe.warlock3.stormfront.network.StormfrontClient
import java.net.UnknownHostException

// FIXME: This needs some re-organization
class SgeViewModel(
    private val clientSettingRepository: ClientSettingRepository,
    private val accountRepository: AccountRepository,
    private val clipboardManager: ClipboardManager,
    updateGameState: (GameState) -> Unit,
) : AutoCloseable {

    // TODO: Make these configurable?
    private val host = "eaccess.play.net"
    private val port = 7900

    private val _state = mutableStateOf<SgeViewState>(SgeViewState.SgeConnecting)
    val state: State<SgeViewState> = _state
    private val backStack = mutableStateListOf<SgeViewState>()

    private val client = SgeClient(host = host, port = port)
    private val scope = CoroutineScope(Dispatchers.IO)
    private val job: Job

    private var accountId: String? = null
    private var characterName: String? = null
    private var gameCode: String? = null

    val lastAccount: Flow<Account?> = flow {
        emit(
            clientSettingRepository.get("lastUsername")?.let { username ->
                accountRepository.getByUsername(username)
            }
        )
    }

    init {
        job = scope.launch {
            if (client.connect().isFailure) {
                println("Failed to connect to server")
                _state.value = SgeViewState.SgeError("Failed to connect to server")
                return@launch
            }
            navigate(SgeViewState.SgeAccountSelector)
            client.eventFlow.collect { event ->
                println("Got event: $event")
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
                            println("Got character list in unexpected state")
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
                                accountId!!,
                                "$gameCode:$characterName".lowercase(),
                                gameCode!!,
                                characterName!!
                            )

                        scope.launch {
                            AppContainer.characterRepository.saveCharacter(character)
                            AppContainer.clientSettings.put("lastUsername", accountId!!)
                        }

                        try {
                            val client = StormfrontClient(
                                host = credentials.host,
                                port = credentials.port,
                                windowRepository = AppContainer.windowRepository,
                                characterRepository = AppContainer.characterRepository,
                                scriptEngineRegistry = AppContainer.scriptEngineRegistry,
                                alterationRepository = AppContainer.alterationRepository,
                                streamRegistry = AppContainer.streamRegistry,
                            )
                            client.connect(credentials.key)
                            val gameViewModel = AppContainer.gameViewModelFactory(client, clipboardManager)
                            updateGameState(
                                GameState.ConnectedGameState(gameViewModel)
                            )
                        } catch (e: UnknownHostException) {
                            updateGameState(GameState.ErrorState("Unknown host: ${e.message}"))
                        }
                        close()
                    }
                }
            }
        }
    }

    fun accountSelected(account: Account) {
        _state.value = SgeViewState.SgeLoadingGameList
        accountId = account.username
        client.login(account.username, account.password)
    }

    fun gameSelected(game: SgeGame) {
        _state.value = SgeViewState.SgeLoadingCharacterList(game)
        gameCode = game.code
        client.selectGame(game.code)
    }

    fun characterSelected(game: SgeGame, character: SgeCharacter) {
        _state.value = SgeViewState.SgeConnectingToGame(game, character)
        characterName = character.name
        client.selectCharacter(character.code)
    }

    fun goBack() {
        backStack.removeLast()
        _state.value = backStack.last()
    }

    fun saveAccount(account: Account) {
        scope.launch {
            accountRepository.save(account)
        }
    }

    private fun navigate(newState: SgeViewState) {
        backStack += listOf(newState)
        _state.value = newState
    }

    override fun close() {
        client.close()
        job.cancel()
    }
}

sealed class SgeViewState {
    object SgeConnecting : SgeViewState()
    object SgeAccountSelector : SgeViewState()
    class SgeGameSelector(val games: List<SgeGame>) : SgeViewState()
    class SgeCharacterSelector(val game: SgeGame, val characters: List<SgeCharacter>) : SgeViewState()
    object SgeLoadingGameList : SgeViewState()
    data class SgeLoadingCharacterList(val game: SgeGame) : SgeViewState()
    data class SgeConnectingToGame(val game: SgeGame, val character: SgeCharacter) : SgeViewState()
    class SgeError(val error: String) : SgeViewState()
}