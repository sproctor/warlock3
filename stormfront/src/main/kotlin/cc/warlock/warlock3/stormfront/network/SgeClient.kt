package cc.warlock.warlock3.stormfront.network

import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.Integer.min
import java.net.Socket
import java.net.SocketException
import kotlin.concurrent.thread

class SgeClient {
    companion object {
        const val host: String = "eaccess.play.net"
        const val port: Int = 7900
    }

    var socket: Socket = Socket()
    val listeners = ArrayList<SgeConnectionListener>()
    var passwordHash: String? = null
    var username: String? = null

    fun connect() {
        println("connecting...")

        socket = Socket(host, port)
        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))

        // request password hash
        send("K\n")

        thread(start = true) {
            while (socket.isConnected && !socket.isClosed) {
                try {
                    val line = reader.readLine()
                    if (line != null) {
                        handleData(line)
                    }
                } catch (e: SocketException) {
                    // socket closed
                }
            }
        }
    }

    private fun handleData(line: String) {
        println("SGE receive: $line")

        if (passwordHash == null) {
            passwordHash = line
            notifyListeners(SgeLoginReadyEvent())
            return
        }

        val response = parseServerResponse(line)
        when (response) {
            is SgeError -> {
                socket.close()
                notifyListeners(SgeErrorEvent(response))
            }
            is SgeLoginSucceededResponse -> {
                // request game list
                send("M\n")
                notifyListeners(SgeLoginSucceededEvent())
            }
            is SgeGameListResponse -> notifyListeners(SgeGamesReadyEvent(response.games))
            is SgeGameDetailsResponse -> send("C\n") // Ask for character list
            is SgeCharacterListResponse -> notifyListeners(SgeCharactersReadyEvent(response.characters))
            is SgeReadyToPlayResponse -> {
                socket.close()
                notifyListeners(SgeReadyToPlayEvent(response.properties))
            }
        }
    }

    private fun parseServerResponse(line: String): SgeResponse {
        return when (line[0]) {
            'A' -> {
                // response from login attempt
                when {
                    line.startsWith("A\tPASSWORD") -> SgeError.INVALID_PASSWORD
                    line.startsWith("A\tREJECT") -> SgeError.ACCOUNT_REJECTED
                    line.startsWith("A\tNORECORD") -> SgeError.INVALID_ACCOUNT
                    line.startsWith("A\t$username", true) -> SgeLoginSucceededResponse()
                    else -> SgeError.UNKNOWN_ERROR
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
                SgeGameListResponse(games)
            }
            // We're ignoring the details. Some might be interesting if your account is deactivated
            'G' -> SgeGameDetailsResponse()
            'C' -> {
                val characters = ArrayList<SgeCharacter>()
                val tokens = line.split("\t").drop(5)
                for (i in 1 until tokens.size step 2) {
                    characters.add(SgeCharacter(tokens[i], tokens[i - 1]))
                }
                SgeCharacterListResponse(characters)
            }
            'L' -> {
                val tokens = line.split("\t")
                val status = tokens[1]
                when (status) {
                    "OK" -> {
                        val properties = HashMap<String, String>()
                        for (element in tokens.drop(2)) {
                            val property = element.split("=")
                            properties[property[0]] = property[1]
                        }
                        SgeReadyToPlayResponse(properties)
                    }
                    "PROBLEM" -> SgeError.ACCOUNT_EXPIRED
                    else -> SgeError.UNKNOWN_ERROR
                }
            }
            else -> SgeUnrecognizedResponse()
        }
    }

    private fun send(string: String) {
        println("SGE send: $string")
        socket.getOutputStream().write(string.toByteArray(Charsets.US_ASCII))
    }

    private fun send(bytes: ByteArray) {
        println("SGE send: " + bytes.toString())
        socket.getOutputStream().write(bytes)
    }
    fun addListener(listener: SgeConnectionListener) {
        listeners.add(listener)
    }

    private fun notifyListeners(event: SgeEvent) {
        listeners.forEach { it.event(event) }
    }

    fun login(username: String, password: String) {
        this.username = username
        val output = "A\t$username\t".toByteArray(Charsets.US_ASCII) + encryptPassword(password) + '\n'.toByte()
        send(output)
    }

    private fun encryptPassword(password: String): ByteArray {
        val length = min(password.length, passwordHash!!.length)
        return ByteArray(length, { n -> ((passwordHash!![n].toInt() xor (password[n].toInt() - 32)) + 32).toByte() })
    }

    fun selectGame(game: SgeGame) {
        send("G\t" + game.code + "\n")
    }

    fun selectCharacter(character: SgeCharacter) {
        send("L\t" + character.code + "\tSTORM\n")
    }
}

data class SgeGame(val title: String, val code: String, val status: String?)
data class SgeCharacter(val name: String, val code: String)

interface SgeResponse
class SgeLoginSucceededResponse : SgeResponse
class SgeGameListResponse(val games: List<SgeGame>) : SgeResponse
class SgeGameDetailsResponse() : SgeResponse
class SgeCharacterListResponse(val characters: List<SgeCharacter>) : SgeResponse
class SgeReadyToPlayResponse(val properties: Map<String, String>) : SgeResponse
class SgeUnrecognizedResponse : SgeResponse

enum class SgeError : SgeResponse {
    INVALID_PASSWORD, INVALID_ACCOUNT, ACCOUNT_REJECTED, ACCOUNT_EXPIRED, UNKNOWN_ERROR
}

interface SgeConnectionListener {
    fun event(event: SgeEvent)
}

interface SgeEvent
class SgeLoginReadyEvent : SgeEvent
class SgeLoginSucceededEvent : SgeEvent
data class SgeGamesReadyEvent(val games: List<SgeGame>) : SgeEvent
data class SgeCharactersReadyEvent(val characters: List<SgeCharacter>) : SgeEvent
data class SgeReadyToPlayEvent(val loginProperties: Map<String, String>) : SgeEvent
data class SgeErrorEvent(val errorCode: SgeError) : SgeEvent