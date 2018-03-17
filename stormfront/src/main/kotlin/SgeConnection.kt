package cc.warlock.warlock3.stormfront

import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.Integer.max
import java.lang.Integer.min
import java.net.Socket
import java.nio.charset.Charset
import kotlin.concurrent.thread

class SgeConnection {
    companion object {
        const val host: String = "eaccess.play.net"
        const val port: Int = 7900
    }

    var socket: Socket = Socket()
    val listeners = ArrayList<SgeConnectionListener>()
    var passwordHash: String? = null

    fun connect() {
        println("connecting...")

        socket = Socket(host, port)
        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))

        // request password hash
        send("K\n")

        thread(start = true) {
            while (isSocketActive()) {
                val line = reader.readLine()
                if (line != null) {
                    handleData(line)
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

        when (line[0]) {
            'A' -> {
                // response from login attempt
                if (line.startsWith("A\t")) {
                    val errorCode = when {
                        line.startsWith("A\tPASSWORD") -> SgeError.INVALID_PASSWORD
                        line.startsWith("A\tREJECT") -> SgeError.ACCOUNT_REJECTED
                        line.startsWith("A\tNORECORD") -> SgeError.INVALID_ACCOUNT
                        else -> SgeError.UNKNOWN_ERROR
                    }
                    notifyListeners(SgeErrorEvent(errorCode))
                } else {
                    // request game list
                    send("M\n")
                    notifyListeners(SgeLoginSucceededEvent())
                }
            }
            // response for request for games
            'M' -> {
                val games = ArrayList<SgeGame>()
                // tab delineated list of games
                val tokens = line.split("\t")
                // drop the M
                tokens.drop(1)
                for (i in 0..)
            }
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
        for (listener in listeners) {
            listener.event(event)
        }
    }

    private fun isSocketActive(): Boolean {
        return socket.isConnected() || !socket.isClosed() || !socket.isInputShutdown() || !socket.isOutputShutdown()
    }

    fun login(username: String, password: String) {
        val encryptedPassword = encryptPassword(password)
        val output = "A\t$username\t".toByteArray(Charsets.US_ASCII) + encryptedPassword + '\n'.toByte()
        send(output)
    }

    private fun encryptPassword(password: String): ByteArray {
        val length = min(password.length, passwordHash!!.length)
        return ByteArray(length, { n -> ((passwordHash!![n].toInt() xor (password[n].toInt() - 32)) + 32).toByte() })
    }
}

enum class SgeError {
    INVALID_PASSWORD, INVALID_ACCOUNT, ACCOUNT_REJECTED, ACCOUNT_EXPIRED, UNKNOWN_ERROR
}

interface SgeConnectionListener {
    fun event(event: SgeEvent)
}

open class SgeEvent

class SgeLoginReadyEvent : SgeEvent()

class SgeLoginSucceededEvent : SgeEvent()

data class SgeGamesReadyEvent(val games: Collection<SgeGame>) : SgeEvent()

data class SgeCharactersReadyEvent(val characters: Map<String, String>) : SgeEvent()

data class SgeReadyToPlayEvent(val properties: Map<String, String>) : SgeEvent()

data class SgeErrorEvent(val errorCode: SgeError) : SgeEvent()