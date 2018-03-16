package cc.warlock.warlock3.stormfront

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket
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
        println("SGE: $line")
        System.out.flush()

        if (passwordHash == null) {
            passwordHash = line
            notifyListeners(SgeLoginReadyEvent())
            return
        }

        when (line[0]) {
            'A' -> {

            }
        }
    }

    private fun send(string: String) {
        socket.getOutputStream().write(string.toByteArray(Charsets.US_ASCII))
    }

    private fun notifyListeners(event: SgeEvent) {
        for (listener in listeners) {
            listener.event(event)
        }
    }

    private fun isSocketActive(): Boolean {
        return socket.isConnected() || !socket.isClosed() || !socket.isInputShutdown() || !socket.isOutputShutdown()
    }
}

enum class SgeError {
    INVALID_PASSWORD, INVALID_ACCOUNT, ACCOUNT_REJECTED, ACCOUNT_EXPIRED
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