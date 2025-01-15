package warlockfe.warlock3.compose.ui.dashboard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import warlockfe.warlock3.compose.model.GameScreen
import warlockfe.warlock3.compose.model.GameState
import warlockfe.warlock3.compose.ui.game.GameViewModelFactory
import warlockfe.warlock3.core.client.CharacterProxySettings
import warlockfe.warlock3.core.client.GameCharacter
import warlockfe.warlock3.core.client.WarlockClientFactory
import warlockfe.warlock3.core.prefs.AccountRepository
import warlockfe.warlock3.core.prefs.CharacterRepository
import warlockfe.warlock3.core.prefs.CharacterSettingsRepository
import warlockfe.warlock3.core.sge.SgeClientFactory
import warlockfe.warlock3.core.sge.SgeEvent
import warlockfe.warlock3.core.sge.SimuGameCredentials
import warlockfe.warlock3.stormfront.network.StormfrontClient
import java.net.UnknownHostException

class DashboardViewModel(
    characterRepository: CharacterRepository,
    private val characterSettingsRepository: CharacterSettingsRepository,
    private val accountRepository: AccountRepository,
    private val gameState: GameState,
    private val sgeClientFactory: SgeClientFactory,
    private val warlockClientFactory: WarlockClientFactory,
    private val gameViewModelFactory: GameViewModelFactory,
    private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    val characters = characterRepository.observeAllCharacters()

    private val logger = KotlinLogging.logger { }

    var busy by mutableStateOf(false)
        private set

    var message: String? by mutableStateOf(null)
        private set

    // TODO: Make these configurable?
    private val host = "eaccess.play.net"
    private val port = 7900

    private var connectJob: Job? = null

    fun connectCharacter(character: GameCharacter) {
        if (busy) return
        busy = true
        connectJob?.cancel()
        connectJob = viewModelScope.launch(ioDispatcher) {
            try {
                message = "Connecting..."
                val sgeClient = sgeClientFactory.create(host = host, port = port)
                val result = sgeClient.connect()
                if (result.isFailure) {
                    logger.error(result.exceptionOrNull()) { "Unable to connect to server" }
                    message = "Could not connect to SGE"
                    return@launch
                }
                viewModelScope.launch(Dispatchers.IO) {
                    sgeClient.eventFlow
                        .collect { event ->
                            when (event) {
                                is SgeEvent.SgeLoginSucceededEvent -> sgeClient.selectGame(character.gameCode)
                                is SgeEvent.SgeGameSelectedEvent -> sgeClient.requestCharacterList()
                                is SgeEvent.SgeCharactersReadyEvent -> {
                                    val characters = event.characters
                                    val sgeCharacter = characters.firstOrNull { it.name.equals(character.name, true) }
                                    if (sgeCharacter == null) {
                                        message = "Could not find character: ${character.name}"
                                    } else {
                                        sgeClient.selectCharacter(sgeCharacter.code)
                                    }
                                }

                                is SgeEvent.SgeReadyToPlayEvent -> {
                                    try {
                                        connectToGame(event.credentials, character.id)
                                    } catch (e: UnknownHostException) {
                                        gameState.screen = GameScreen.ErrorState("Unknown host: ${e.message}")
                                    } catch (e: Exception) {
                                        logger.error(e) { "Error connecting to server" }
                                        gameState.screen = GameScreen.ErrorState("Error: ${e.message}")
                                    }
                                    sgeClient.close()
                                    cancelConnect()
                                }

                                is SgeEvent.SgeErrorEvent -> {
                                    message = "Error code (${event.errorCode})"
                                    connectJob?.cancel()
                                }

                                else -> Unit // we don't care?
                            }
                        }
                }
                val account = character.accountId?.let { accountRepository.getByUsername(it) }
                if (account == null) {
                    message = "Invalid account"
                    return@launch
                }
                sgeClient.login(username = account.username, account.password ?: "")
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

    suspend fun getProxySettings(characterId: String): CharacterProxySettings {
        return characterSettingsRepository.getProxySettings(characterId)
    }

    fun updateProxySettings(characterId: String, proxySettings: CharacterProxySettings) {
        viewModelScope.launch {
            characterSettingsRepository.saveProxySettings(characterId, proxySettings)
        }
    }

    private suspend fun connectToGame(credentials: SimuGameCredentials, characterId: String) {
        var loginCredentials = credentials
        val proxySettings = getProxySettings(characterId)
        var process: Process? = null
        if (proxySettings.enabled) {
            val substitutions = mapOf(
                "{host}" to loginCredentials.host,
                "{port}" to loginCredentials.port.toString(),
            )
            val proxyCommand = proxySettings.launchCommand?.substitute(substitutions)
            val proxyHost = proxySettings.host?.substitute(substitutions) ?: "localhost"
            val proxyPort = proxySettings.port?.substitute(substitutions)?.toInt() ?: loginCredentials.port
            loginCredentials = credentials.copy(
                host = proxyHost,
                port = proxyPort,
            )
            if (proxyCommand != null) {
                logger.debug { "Launching proxy command: $proxyCommand" }
                process = Runtime.getRuntime().exec(proxyCommand)
                delay(proxySettings.delay ?: 1000L)
            }
        }
        val sfClient = warlockClientFactory.createStormFrontClient(
            loginCredentials,
            gameState.windowRepository,
            gameState.streamRegistry,
        ) as StormfrontClient
        process?.let { sfClient.setProxy(it) }
        sfClient.connect()
        val gameViewModel = gameViewModelFactory.create(
            sfClient,
            gameState.windowRepository,
            gameState.streamRegistry
        )
        gameState.screen = GameScreen.ConnectedGameState(gameViewModel)
    }
}

private fun String.substitute(substitutions: Map<String, String>): String {
    var result = this
    substitutions.forEach { (key, value) ->
        result = result.replace(key, value)
    }
    return result
}
