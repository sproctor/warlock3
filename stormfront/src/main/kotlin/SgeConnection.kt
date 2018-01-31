package cc.warlock.warlock3.stormfront

import java.net.Socket

class SgeConnection {
    var state: SgeState = SgeState.INITIAL
    var socket: Socket? = null

    val host: String = "eaccess.play.net"
    val port: Int = 7900

    fun connect() {
        state = SgeState.INITIAL
        socket = Socket(host, port)
    }
}

enum class SgeState {
    INITIAL, KEY, ACCOUNT, MENU, GAME, PICK, CHARACTERS, LOAD
}