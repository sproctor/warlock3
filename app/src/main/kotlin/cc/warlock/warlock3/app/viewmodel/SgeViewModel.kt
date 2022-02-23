package cc.warlock.warlock3.app.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import cc.warlock.warlock3.app.GameState
import cc.warlock.warlock3.core.prefs.AccountRepository
import cc.warlock.warlock3.core.prefs.ClientSettingRepository
import cc.warlock.warlock3.core.prefs.models.Account
import cc.warlock.warlock3.core.prefs.models.GameCharacter
import cc.warlock.warlock3.stormfront.network.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

// FIXME: This needs some re-organization
class SgeViewModel(
    private val clientSettingRepository: ClientSettingRepository,
    private val accountRepository: AccountRepository,
    readyToPlay: (GameState) -> Unit,
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

    val lastAccount = flow<Account?> {
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
                            navigate(SgeViewState.SgeCharacterSelector(currentState.game, event.characters))
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
                        val properties = event.loginProperties
                        val key = properties["KEY"]!!
                        val host = properties["GAMEHOST"]!!
                        val port = properties["GAMEPORT"]!!.toInt()
                        val character =
                            GameCharacter(accountId!!, "$gameCode:$characterName", gameCode!!, characterName!!)

                        readyToPlay(
                            GameState.ConnectedGameState(host = host, port = port, key = key, character = character)
                        )
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
        gameCode = game.code.lowercase()
        client.selectGame(game)
    }

    fun characterSelected(game: SgeGame, character: SgeCharacter) {
        _state.value = SgeViewState.SgeConnectingToGame(game, character)
        characterName = character.name.lowercase()
        client.selectCharacter(character)
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