package warlockfe.warlock3.stormfront.network

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import warlockfe.warlock3.core.sge.SgeCharacter
import warlockfe.warlock3.core.sge.SgeClient
import warlockfe.warlock3.core.sge.SgeError
import warlockfe.warlock3.core.sge.SgeEvent
import warlockfe.warlock3.core.sge.SgeGame
import warlockfe.warlock3.core.sge.SimuGameCredentials
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.lang.Integer.min
import java.net.Socket
import java.nio.charset.Charset
import kotlinx.io.IOException

private const val charsetName = "windows-1252"
private val charset = Charset.forName(charsetName)

class SgeClientImpl(
    private val ioDispatcher: CoroutineDispatcher,
) : SgeClient {

    private val logger = KotlinLogging.logger {}
    private var outputStream: OutputStream? = null
    private var stopped = false
    private val _eventFlow = MutableSharedFlow<SgeEvent>()
    override val eventFlow = _eventFlow.asSharedFlow()
    private var passwordHash: ByteArray? = null
    private var username: String? = null

    private val scope = CoroutineScope(ioDispatcher + SupervisorJob())

    override suspend fun connect(host: String, port: Int): Boolean {
        return withContext(ioDispatcher) {
            try {
                logger.debug { "connecting..." }

                val socket = Socket(host, port)
                val reader = BufferedReader(InputStreamReader(socket.inputStream, charsetName))
                outputStream = socket.outputStream

                // request password hash
                send("K\n")
                passwordHash = reader.readLine()?.toByteArray(Charsets.US_ASCII)
                    ?: return@withContext false

                scope.launch {
                    try {
                        while (!stopped) {
                            val line = reader.readLine()
                            if (line != null) {
                                handleData(line)
                            } else {
                                // connection closed by server
                                stopped = true
                            }
                        }
                    } catch (e: IOException) {
                        // not sure why, but let's retry!
                        logger.debug { "SGE  exception: ${e.message}" }
                        _eventFlow.emit(SgeEvent.SgeErrorEvent(SgeError.UNKNOWN_ERROR))
                    } finally {
                        logger.debug { "Closing socket" }
                        reader.close()
                        socket.close()
                    }
                }
                true
            } catch (_: IOException) {
                false
            }
        }
    }

    private suspend fun handleData(line: String) {
        logger.debug { "SGE receive: $line" }

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
                stopped = true
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
                logger.debug { "A line ($line)" }
                logger.debug {
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

    private suspend fun send(string: String) {
        withContext(ioDispatcher) {
            logger.debug { "SGE send: $string" }
            outputStream?.write(string.toByteArray(charset))
            outputStream?.flush()
        }
    }

    private suspend fun send(bytes: ByteArray) {
        withContext(ioDispatcher) {
            logger.debug { "SGE send: ${bytes.decodeToString()}" }
            outputStream?.write(bytes)
            outputStream?.flush()
        }
    }

    override suspend fun login(username: String, password: String) {
        this@SgeClientImpl.username = username
        val encryptedPassword = encryptPassword(password.toByteArray(Charsets.US_ASCII))
        val output = "A\t$username\t".toByteArray(Charsets.US_ASCII) + encryptedPassword + '\n'.code.toByte()
        send(output)
    }

    private fun encryptPassword(password: ByteArray): ByteArray {
        val hash = passwordHash!!
        val length = min(password.size, hash.size)
        return ByteArray(length) { n ->
            ((hash[n].toInt() xor (password[n].toInt() - 32)) + 32).toByte()
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
        stopped = true
        scope.cancel()
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
