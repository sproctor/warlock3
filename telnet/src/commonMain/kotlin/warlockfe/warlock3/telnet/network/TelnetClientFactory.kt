package warlockfe.warlock3.telnet.network

import kotlinx.coroutines.CoroutineDispatcher
import warlockfe.warlock3.core.prefs.repositories.CharacterRepository
import warlockfe.warlock3.core.prefs.repositories.LoggingRepository
import warlockfe.warlock3.core.window.WindowRegistry

class TelnetClientFactory(
    private val characterRepository: CharacterRepository,
    private val loggingRepository: LoggingRepository,
    private val ioDispatcher: CoroutineDispatcher,
) {
    fun create(
        windowRegistry: WindowRegistry,
        socket: TelnetSocket,
        gameCode: String,
        character: String,
        acceptScripts: Boolean,
    ): TelnetClient =
        TelnetClient(
            gameCode = gameCode,
            character = character,
            acceptScripts = acceptScripts,
            characterRepository = characterRepository,
            windowRegistry = windowRegistry,
            fileLogging = loggingRepository,
            ioDispatcher = ioDispatcher,
            socket = socket,
        )
}
