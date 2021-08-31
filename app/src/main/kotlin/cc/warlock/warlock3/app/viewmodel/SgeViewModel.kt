package cc.warlock.warlock3.app.viewmodel

import cc.warlock.warlock3.app.model.Account
import cc.warlock.warlock3.stormfront.network.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class SgeViewModel(
    readyToPlay: (Map<String, String>) -> Unit
) {
    private val _state = MutableStateFlow<SgeViewState>(SgeViewState.SgeLoading("Connecting to SGE"))
    private val backStack = MutableStateFlow<List<SgeViewState>>(emptyList())
    val state = _state.asStateFlow()
    private val client = SgeClient()
    private val scope = CoroutineScope(Dispatchers.IO)
    private val job: Job

    init {
        job = scope.launch {
            client.connect()
            navigate(SgeViewState.SgeAccountSelector)
            client.eventFlow.collect { event ->
                println("Got event: $event")
                when (event) {
                    SgeEvent.SgeLoginSucceededEvent -> client.requestGameList()
                    is SgeEvent.SgeGamesReadyEvent -> navigate(SgeViewState.SgeGameSelector(event.games))
                    is SgeEvent.SgeGameSelectedEvent -> client.requestCharacterList()
                    is SgeEvent.SgeCharactersReadyEvent -> navigate(SgeViewState.SgeCharacterSelector(event.characters))
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
                        _state.value = SgeViewState.SgeLoading("Loading game")
                        readyToPlay(event.loginProperties)
                    }
                }
            }
            println("Done collecting")
        }
    }

    fun accountSelected(account: Account) {
        _state.value = SgeViewState.SgeLoading("Logging in")
        client.login(account.name, account.password)
    }

    fun gameSelected(game: SgeGame) {
        _state.value = SgeViewState.SgeLoading("Selecting game")
        client.selectGame(game)
    }

    fun characterSelected(character: SgeCharacter) {
        _state.value = SgeViewState.SgeLoading("Selecting character")
        client.selectCharacter(character)
    }

    fun goBack() {
        this.backStack.value = backStack.value.dropLast(1)
        _state.value = backStack.value.last()
    }

    private fun navigate(newState: SgeViewState) {
        backStack.value = backStack.value + listOf(newState)
        _state.value = newState
    }

    fun close() {
        client.close()
        job.cancel()
    }
}

sealed class SgeViewState {
    object SgeAccountSelector : SgeViewState()
    class SgeGameSelector(val games: List<SgeGame>) : SgeViewState()
    class SgeCharacterSelector(val characters: List<SgeCharacter>) : SgeViewState()
    class SgeLoading(val message: String) : SgeViewState()
    class SgeError(val error: String) : SgeViewState()
}