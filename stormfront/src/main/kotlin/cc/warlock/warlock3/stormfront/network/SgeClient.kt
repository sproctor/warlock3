package cc.warlock.warlock3.stormfront.network

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okio.*
import java.lang.Integer.min
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException

class SgeClient(
    private val host: String,
    private val port: Int
) {
    private var sink: BufferedSink? = null
    private var stopped = false
    private val _eventFlow = MutableSharedFlow<SgeEvent>()
    val eventFlow = _eventFlow.asSharedFlow()
    private var passwordHash: ByteArray? = null
    private var username: String? = null

    private val scope = CoroutineScope(Dispatchers.IO)

    suspend fun connect() = withContext(Dispatchers.IO) {
        runCatching {
            println("connecting...")

            val socket = Socket(host, port)
            sink = socket.sink().buffer()
            val source = socket.source().buffer()

            // request password hash
            send("K\n")
            passwordHash = source.readUtf8Line()?.toByteArray(Charsets.US_ASCII)
                ?: throw IOException("Error getting password hash")

            scope.launch {
                runCatching {
                    try {
                        while (!stopped) {
                            try {
                                val line = source.readUtf8Line()
                                if (line != null) {
                                    handleData(line)
                                } else {
                                    // connection closed by server
                                    stopped = true
                                }
                            } catch (e: SocketException) {
                                // not sure why, but let's retry!
                                println("SGE socket exception: " + e.message)
                            } catch (_: SocketTimeoutException) {
                                // Timeout, let's retry!
                                println("Timed out connecting to server")
                            }
                        }
                    } finally {
                        println("Closing socket")
                        source.close()
                        sink?.close()
                        socket.close()
                    }
                }
            }
        }
    }

    private suspend fun handleData(line: String) {
        println("SGE receive: $line")

        when (val response = parseServerResponse(line)) {
            is SgeResponse.SgeErrorResponse -> {
                _eventFlow.emit(SgeEvent.SgeErrorEvent(response.error))
            }
            SgeResponse.SgeLoginSucceededResponse -> _eventFlow.emit(SgeEvent.SgeLoginSucceededEvent)
            is SgeResponse.SgeGameListResponse -> _eventFlow.emit(SgeEvent.SgeGamesReadyEvent(response.games))
            SgeResponse.SgeGameDetailsResponse -> _eventFlow.emit(SgeEvent.SgeGameSelectedEvent)
            is SgeResponse.SgeCharacterListResponse -> _eventFlow.emit(SgeEvent.SgeCharactersReadyEvent(response.characters))
            is SgeResponse.SgeReadyToPlayResponse -> {
                stopped = true
                _eventFlow.emit(SgeEvent.SgeReadyToPlayEvent(response.properties))
            }
        }
    }

    private fun parseServerResponse(line: String): SgeResponse {
        return when (line[0]) {
            'A' -> {
                // response from login attempt
                println("A line ($line)")
                println("as bytes: ${line.toByteArray().map { it.toInt().toString(radix = 16).padStart(2, '0') }}")
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

    private fun send(string: String) {
        println("SGE send: $string")
        sink?.writeUtf8(string)
        sink?.flush()
    }

    private fun send(bytes: ByteArray) {
        println("SGE send: $bytes")
        sink?.write(bytes)
        sink?.flush()
    }

    fun login(username: String, password: String) {
        this.username = username
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

    fun requestGameList() {
        // request game list
        send("M\n")
    }

    fun requestCharacterList() {
        send("C\n")
    }

    fun selectGame(game: SgeGame) {
        send("G\t${game.code}\n")
    }

    fun selectCharacter(character: SgeCharacter) {
        send("L\t${character.code}\tSTORM\n")
    }

    fun close() {
        stopped = true
        scope.cancel()
    }
}

data class SgeGame(val title: String, val code: String, val status: String?)
data class SgeCharacter(val name: String, val code: String)

sealed class SgeResponse {
    object SgeLoginSucceededResponse : SgeResponse()
    data class SgeGameListResponse(val games: List<SgeGame>) : SgeResponse()
    object SgeGameDetailsResponse : SgeResponse()
    data class SgeCharacterListResponse(val characters: List<SgeCharacter>) : SgeResponse()
    data class SgeReadyToPlayResponse(val properties: Map<String, String>) : SgeResponse()
    object SgeUnrecognizedResponse : SgeResponse()
    data class SgeErrorResponse(val error: SgeError) : SgeResponse()
}

enum class SgeError {
    INVALID_PASSWORD, INVALID_ACCOUNT, ACCOUNT_REJECTED, ACCOUNT_EXPIRED, UNKNOWN_ERROR
}

sealed class SgeEvent {
    object SgeLoginSucceededEvent : SgeEvent()
    data class SgeGamesReadyEvent(val games: List<SgeGame>) : SgeEvent()
    object SgeGameSelectedEvent : SgeEvent()
    data class SgeCharactersReadyEvent(val characters: List<SgeCharacter>) : SgeEvent()
    data class SgeReadyToPlayEvent(val loginProperties: Map<String, String>) : SgeEvent()
    data class SgeErrorEvent(val errorCode: SgeError) : SgeEvent()
}