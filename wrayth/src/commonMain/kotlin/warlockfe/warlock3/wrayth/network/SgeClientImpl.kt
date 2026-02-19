package warlockfe.warlock3.wrayth.network

import co.touchlab.kermit.Logger
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.isClosed
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.network.tls.tls
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.core.toByteArray
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.writeByteArray
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import warlockfe.warlock3.core.sge.AutoConnectResult
import warlockfe.warlock3.core.sge.SgeCharacter
import warlockfe.warlock3.core.sge.SgeClient
import warlockfe.warlock3.core.sge.SgeError
import warlockfe.warlock3.core.sge.SgeEvent
import warlockfe.warlock3.core.sge.SgeGame
import warlockfe.warlock3.core.sge.SgeSettings
import warlockfe.warlock3.core.sge.SimuGameCredentials
import warlockfe.warlock3.core.sge.StoredConnection
import warlockfe.warlock3.core.util.decodeWindows1252
import warlockfe.warlock3.core.util.encodeWindows1252
import warlockfe.warlock3.wrayth.util.configureTLS
import kotlin.math.min

private const val passwordHashLength = 32

class SgeClientImpl(
    private val ioDispatcher: CoroutineDispatcher,
) : SgeClient {

    private val logger = Logger.withTag("SgeClient")
    val selectorManager: SelectorManager = SelectorManager()
    private var socket: Socket? = null
    private lateinit var receiveChannel: ByteReadChannel
    private lateinit var sendChannel: ByteWriteChannel
    private val _eventFlow = MutableSharedFlow<SgeEvent>()
    override val eventFlow = _eventFlow.asSharedFlow()
    private val buffer = ByteArray(8192)
    private val passwordHash: ByteArray = ByteArray(32)
    private var username: String? = null
    private var secure: Boolean = true

    private val scope = CoroutineScope(ioDispatcher + SupervisorJob())

    override suspend fun connect(settings: SgeSettings): Boolean {
        secure = settings.secure
        return withContext(ioDispatcher) {
            try {
                logger.d { "connecting..." }
                val socket: Socket = if (secure) {
                    aSocket(selectorManager)
                        .tcp()
                        .connect(settings.host, settings.port)
                        .tls(coroutineContext = scope.coroutineContext) {
                            configureTLS(settings.certificate)
                        }
                } else {
                    aSocket(selectorManager).tcp().connect(settings.host, settings.port)
                }
                this@SgeClientImpl.socket = socket
                receiveChannel = socket.openReadChannel()
                sendChannel = socket.openWriteChannel(autoFlush = true)

                // request password hash
                send("K\n")

                val hashLength = receiveChannel.readAvailable(buffer)
                if (hashLength <= 0) {
                    logger.e { "Error reading password hash" }
                    return@withContext false
                }
                buffer.copyInto(passwordHash, endIndex = min(hashLength, passwordHashLength))

                scope.launch {
                    try {
                        while (!socket.isClosed) {
                            val line = readline()
                            if (line != null) {
                                handleData(line)
                            } else {
                                // connection closed by server
                                socket.close()
                            }
                        }
                    } catch (e: IOException) {
                        // not sure why, but let's retry!
                        logger.d { "SGE  exception: ${e.message}" }
                        _eventFlow.emit(SgeEvent.SgeErrorEvent(SgeError.UNKNOWN_ERROR))
                    } finally {
                        logger.d { "Closing socket" }
                        socket.close()
                    }
                }
                true
            } catch (e: Exception) {
                logger.d(e) { "SGE exception: ${e.message}" }
                false
            }
        }
    }

    private suspend fun handleData(line: String) {
        logger.d { "SGE receive: $line" }

        when (val response = parseServerResponse(line)) {
            is SgeResponse.SgeErrorResponse -> {
                _eventFlow.emit(SgeEvent.SgeErrorEvent(response.error))
            }

            SgeResponse.SgeLoginSucceededResponse -> _eventFlow.emit(SgeEvent.SgeLoginSucceededEvent)
            is SgeResponse.SgeGameListResponse -> _eventFlow.emit(
                SgeEvent.SgeGamesReadyEvent(
                    response.games
                )
            )

            SgeResponse.SgeGameDetailsResponse -> _eventFlow.emit(SgeEvent.SgeGameSelectedEvent)
            is SgeResponse.SgeCharacterListResponse -> _eventFlow.emit(
                SgeEvent.SgeCharactersReadyEvent(
                    response.characters
                )
            )

            is SgeResponse.SgeReadyToPlayResponse -> {
                socket?.close()
                val properties = response.properties
                val credentials = SimuGameCredentials(
                    host = properties["GAMEHOST"]!!,
                    port = properties["GAMEPORT"]!!.toInt(),
                    key = properties["KEY"]!!
                )
                _eventFlow.emit(SgeEvent.SgeReadyToPlayEvent(credentials))
            }

            SgeResponse.SgeUnrecognizedResponse -> Unit // TODO: implement?
        }
    }

    private fun parseServerResponse(line: String): SgeResponse {
        return when (line[0]) {
            'A' -> {
                // response from login attempt
                logger.d { "A line ($line)" }
                logger.d {
                    val bytes = line.toByteArray()
                        .map { it.toInt().toString(radix = 16).padStart(2, '0') }
                    "as bytes: $bytes"
                }
                val tokens = line.split('\t')
                when {
                    tokens.size < 3 -> SgeResponse.SgeErrorResponse(SgeError.UNKNOWN_ERROR)
                    tokens[2] == "PASSWORD" -> SgeResponse.SgeErrorResponse(SgeError.INVALID_PASSWORD)
                    tokens[2] == "REJECT" -> SgeResponse.SgeErrorResponse(SgeError.ACCOUNT_REJECTED)
                    tokens[2] == "NORECORD" -> SgeResponse.SgeErrorResponse(SgeError.INVALID_ACCOUNT)
                    tokens[1].equals(username, true) -> SgeResponse.SgeLoginSucceededResponse
                    else -> SgeResponse.SgeErrorResponse(SgeError.UNKNOWN_ERROR)
                }
            }
            // response for request for games
            'M' -> {
                val games = ArrayList<SgeGame>()
                // tab delineated list of games, dropping the M
                val tokens = line.split("\t").drop(1)
                for (i in 1 until tokens.size step 2) {
                    val gameCode = tokens[i - 1]
                    val gameName = tokens[i]
                    games.add(SgeGame(gameName, gameCode, null))
                }
                SgeResponse.SgeGameListResponse(games)
            }
            // We're ignoring the details. Some might be interesting if your account is deactivated
            'G' -> SgeResponse.SgeGameDetailsResponse
            'C' -> {
                val characters = ArrayList<SgeCharacter>()
                val tokens = line.split("\t").drop(5)
                for (i in 1 until tokens.size step 2) {
                    characters.add(SgeCharacter(tokens[i], tokens[i - 1]))
                }
                SgeResponse.SgeCharacterListResponse(characters)
            }

            'L' -> {
                // status\tproperties
                val tokens = line.split("\t")
                when (tokens[1]) {
                    "OK" -> {
                        val properties = mutableMapOf<String, String>()
                        for (element in tokens.drop(2)) {
                            val property = element.split("=")
                            properties[property[0]] = property[1]
                        }
                        SgeResponse.SgeReadyToPlayResponse(properties)
                    }

                    "PROBLEM" -> SgeResponse.SgeErrorResponse(SgeError.ACCOUNT_EXPIRED)
                    else -> SgeResponse.SgeErrorResponse(SgeError.UNKNOWN_ERROR)
                }
            }

            else -> SgeResponse.SgeUnrecognizedResponse
        }
    }

    private suspend fun readline(): String? {
        // TODO: should we implement readline for this?
        val len = receiveChannel.readAvailable(buffer)
        return if (len > 0) {
            buffer.decodeWindows1252(0, len)
        } else {
            null
        }
    }

    private suspend fun send(string: String) {
        withContext(ioDispatcher) {
            logger.d { "SGE send: $string" }
            sendChannel.writeByteArray(string.encodeWindows1252())
        }
    }

    private suspend fun send(bytes: ByteArray) {
        withContext(ioDispatcher) {
            logger.d { "SGE send: ${bytes.decodeToString()}" }
            sendChannel.writeByteArray(bytes)
        }
    }

    override suspend fun login(username: String, password: String) {
        this@SgeClientImpl.username = username
        val encryptedPassword = encryptPassword(password.encodeWindows1252())
        val output = "A\t$username\t".encodeWindows1252() + encryptedPassword + '\n'.code.toByte()
        send(output)
    }

    private fun encryptPassword(password: ByteArray): ByteArray {
        val length = min(password.size, passwordHash.size)
        return ByteArray(length) { n ->
            ((passwordHash[n].toInt() xor (password[n].toInt() - 32)) + 32).toByte()
        }
    }

    override suspend fun requestGameList() {
        // request game list
        send("M\n")
    }

    override suspend fun requestCharacterList() {
        send("C\n")
    }

    override suspend fun selectGame(gameCode: String) {
        send("G\t${gameCode}\n")
    }

    override suspend fun selectCharacter(characterCode: String) {
        send("L\t${characterCode}\tSTORM\n")
    }

    override fun close() {
        socket?.close()
        scope.cancel()
    }

    override suspend fun autoConnect(
        settings: SgeSettings,
        connection: StoredConnection
    ): AutoConnectResult {
        if (!connect(settings)) {
            logger.e { "Unable to connect to server" }
            return AutoConnectResult.Failure("Could not connect to SGE")
        }
        login(username = connection.username, password = connection.password ?: "")

        while (true) {
            when (val event = eventFlow.first()) {
                is SgeEvent.SgeLoginSucceededEvent -> selectGame(connection.code)
                is SgeEvent.SgeGameSelectedEvent -> requestCharacterList()
                is SgeEvent.SgeCharactersReadyEvent -> {
                    val characters = event.characters
                    val sgeCharacter = characters.firstOrNull { it.name.equals(connection.character, true) }
                    if (sgeCharacter == null) {
                        return AutoConnectResult.Failure("Could not find character: ${connection.character}")
                    } else {
                        selectCharacter(sgeCharacter.code)
                    }
                }

                is SgeEvent.SgeReadyToPlayEvent ->
                    return AutoConnectResult.Success(event.credentials)

                is SgeEvent.SgeErrorEvent -> {
                    return AutoConnectResult.Failure("Error code (${event.errorCode})")
                }

                else ->
                    logger.i { "Unrecognized event: $event" }
            }
        }
    }
}

sealed class SgeResponse {
    data object SgeLoginSucceededResponse : SgeResponse()
    data class SgeGameListResponse(val games: List<SgeGame>) : SgeResponse()
    data object SgeGameDetailsResponse : SgeResponse()
    data class SgeCharacterListResponse(val characters: List<SgeCharacter>) : SgeResponse()
    data class SgeReadyToPlayResponse(val properties: Map<String, String>) : SgeResponse()
    data object SgeUnrecognizedResponse : SgeResponse()
    data class SgeErrorResponse(val error: SgeError) : SgeResponse()
}
