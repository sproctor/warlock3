package cc.warlock.warlock3.app.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import cc.warlock.warlock3.app.model.Account
import cc.warlock.warlock3.app.model.GameCharacter
import cc.warlock.warlock3.stormfront.network.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class SgeViewModel(
    host: String,
    port: Int,
    val lastUsername: String?,
    val accounts: List<Account>,
    val characters: List<GameCharacter>,
    readyToPlay: (Map<String, String>) -> Unit,
    val saveAccount: (Account) -> Unit,
) : AutoCloseable {
    private val _state = mutableStateOf<SgeViewState>(SgeViewState.SgeConnecting)
    val state: State<SgeViewState> = _state
    private val backStack = mutableStateListOf<SgeViewState>()

    private val client = SgeClient(host = host, port = port)
    private val scope = CoroutineScope(Dispatchers.IO)
    private val job: Job

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
                        readyToPlay(event.loginProperties)
                        close()
                    }
                }
            }
        }
    }

    fun accountSelected(account: Account) {
        _state.value = SgeViewState.SgeLoadingGameList
        client.login(account.name, account.password)
    }

    fun gameSelected(game: SgeGame) {
        _state.value = SgeViewState.SgeLoadingCharacterList(game)
        client.selectGame(game)
    }

    fun characterSelected(game: SgeGame, character: SgeCharacter) {
        _state.value = SgeViewState.SgeConnectingToGame(game, character)
        client.selectCharacter(character)
    }

    fun goBack() {
        backStack.removeLast()
        _state.value = backStack.last()
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