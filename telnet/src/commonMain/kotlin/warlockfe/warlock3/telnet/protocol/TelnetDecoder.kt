package warlockfe.warlock3.telnet.protocol

/** What the telnet layer found in a run of bytes from the server, in the order it found it. */
sealed interface TelnetEvent {
    /** Application bytes: what is left once the telnet commands are taken out and `IAC IAC` is unescaped. */
    class Data(
        val bytes: ByteArray,
    ) : TelnetEvent {
        override fun equals(other: Any?): Boolean = other is Data && bytes.contentEquals(other.bytes)

        override fun hashCode(): Int = bytes.contentHashCode()

        override fun toString(): String = "Data(${bytes.decodeToString()})"
    }

    /** `IAC GA` or `IAC EOR`: the server marks the end of a prompt. */
    data object Prompt : TelnetEvent

    /** The server took over echoing what the user types (a password prompt), or gave it back. */
    data class Echo(
        val serverEcho: Boolean,
    ) : TelnetEvent

    /**
     * A GMCP message from the server: the package and message name (`Char.Vitals`) and the JSON
     * payload as sent, empty when the message carried none.
     */
    data class Gmcp(
        val name: String,
        val data: String,
    ) : TelnetEvent

    /** Bytes to send back to the server: our answers to its negotiation. */
    class Reply(
        val bytes: ByteArray,
    ) : TelnetEvent {
        override fun equals(other: Any?): Boolean = other is Reply && bytes.contentEquals(other.bytes)

        override fun hashCode(): Int = bytes.contentHashCode()

        override fun toString(): String = "Reply(${bytes.joinToString { (it.toInt() and 0xFF).toString() }})"
    }
}

/**
 * The telnet protocol (RFC 854) as a MUD client needs it: takes the raw bytes off the socket, hands
 * back the application bytes, and answers the server's option negotiation.
 *
 * Nearly every option is declined, because the text mode telnet was designed around is exactly what
 * a MUD sends, and each accepted option is another thing the server may then do to the stream. The
 * ones taken are the ones that change what the text means: [ECHO], so a password the server does not
 * echo is not echoed here either; [EOR] and the plain `GA`, which mark prompts, the one piece of
 * structure a MUD gives its output; [SGA], which is only a formality. [TTYPE] is answered because
 * many MUDs colour their output by what the terminal says it can show, and the answer names the
 * client, UTF-8 and 256-color support (MTTS). [GMCP] is accepted for the out-of-band data it
 * carries - vitals and room exits, which are what the compass and the vitals bars show - and the
 * server is told which packages are wanted.
 *
 * Compression (MCCP) in particular must be declined, since accepting it turns the stream into zlib.
 *
 * Bytes arrive in whatever pieces the network makes of them, so a command or subnegotiation may be
 * cut anywhere; the decoder keeps its place between calls.
 */
class TelnetDecoder {
    private enum class State {
        DATA,
        IAC,
        WILL,
        WONT,
        DO,
        DONT,
        SB,
        SB_IAC,
    }

    private var state = State.DATA

    // The application bytes gathered since the last event was emitted.
    private var data = ByteArray(256)
    private var dataLength = 0

    private val subnegotiation = ArrayList<Byte>()

    // Options the server has turned on (its WILL, our DO), and options it asked us for (its DO, our WILL).
    private val serverOptions = HashSet<Int>()
    private val clientOptions = HashSet<Int>()

    /** Whether the server negotiated GMCP, and so will take a GMCP message from us. */
    val gmcpNegotiated: Boolean
        get() = GMCP in serverOptions

    // Which answer to the server's next TTYPE SEND: the client name, then the terminal, then the
    // MTTS capabilities, and the last one again as the sign that the list is done.
    private var terminalTypeIndex = 0

    fun decode(
        bytes: ByteArray,
        length: Int = bytes.size,
    ): List<TelnetEvent> {
        val events = ArrayList<TelnetEvent>()
        for (i in 0 until length) {
            feed(bytes[i], events)
        }
        flushData(events)
        return events
    }

    private fun feed(
        byte: Byte,
        events: MutableList<TelnetEvent>,
    ) {
        val b = byte.toInt() and 0xFF
        when (state) {
            State.DATA -> {
                if (b == IAC) {
                    state = State.IAC
                } else if (b != NUL) {
                    // NUL pads a bare CR in telnet's line ending (CR NUL) and means nothing in text.
                    appendData(byte)
                }
            }

            State.IAC -> {
                when (b) {
                    IAC -> {
                        appendData(byte)
                        state = State.DATA
                    }

                    WILL -> {
                        state = State.WILL
                    }

                    WONT -> {
                        state = State.WONT
                    }

                    DO -> {
                        state = State.DO
                    }

                    DONT -> {
                        state = State.DONT
                    }

                    SB -> {
                        subnegotiation.clear()
                        state = State.SB
                    }

                    GA, EOR_COMMAND -> {
                        flushData(events)
                        events += TelnetEvent.Prompt
                        state = State.DATA
                    }

                    // NOP, and the rest of the two-byte commands (data mark, break, ...), none of
                    // which mean anything to a MUD client.
                    else -> {
                        state = State.DATA
                    }
                }
            }

            State.WILL -> {
                flushData(events)
                handleWill(b, events)
                state = State.DATA
            }

            State.WONT -> {
                flushData(events)
                handleWont(b, events)
                state = State.DATA
            }

            State.DO -> {
                flushData(events)
                handleDo(b, events)
                state = State.DATA
            }

            State.DONT -> {
                flushData(events)
                handleDont(b, events)
                state = State.DATA
            }

            State.SB -> {
                if (b == IAC) {
                    state = State.SB_IAC
                } else {
                    subnegotiation += byte
                }
            }

            State.SB_IAC -> {
                when (b) {
                    IAC -> {
                        subnegotiation += byte
                        state = State.SB
                    }

                    SE -> {
                        flushData(events)
                        handleSubnegotiation(events)
                        state = State.DATA
                    }

                    // Not a well-formed subnegotiation. Take the IAC as the start of a new command,
                    // with this byte as the command, which is the reading that loses the least.
                    else -> {
                        state = State.IAC
                        feed(byte, events)
                    }
                }
            }
        }
    }

    private fun handleWill(
        option: Int,
        events: MutableList<TelnetEvent>,
    ) {
        when (option) {
            ECHO, SGA, EOR -> {
                if (serverOptions.add(option)) {
                    events += reply(DO, option)
                    if (option == ECHO) events += TelnetEvent.Echo(serverEcho = true)
                }
            }

            GMCP -> {
                if (serverOptions.add(option)) {
                    events += reply(DO, option)
                    // The handshake the GMCP convention asks of a client: say who we are, then
                    // which packages to send.
                    events += TelnetEvent.Reply(gmcpMessage("Core.Hello", CORE_HELLO))
                    events += TelnetEvent.Reply(gmcpMessage("Core.Supports.Set", CORE_SUPPORTS))
                    // The inventory is only sent when asked for; what is wielded comes from it.
                    events += TelnetEvent.Reply(gmcpMessage("Char.Items.Inv", ""))
                }
            }

            else -> {
                events += reply(DONT, option)
            }
        }
    }

    private fun handleWont(
        option: Int,
        events: MutableList<TelnetEvent>,
    ) {
        if (serverOptions.remove(option)) {
            events += reply(DONT, option)
            if (option == ECHO) events += TelnetEvent.Echo(serverEcho = false)
        }
    }

    private fun handleDo(
        option: Int,
        events: MutableList<TelnetEvent>,
    ) {
        when (option) {
            TTYPE -> {
                if (clientOptions.add(option)) {
                    events += reply(WILL, option)
                }
            }

            else -> {
                events += reply(WONT, option)
            }
        }
    }

    private fun handleDont(
        option: Int,
        events: MutableList<TelnetEvent>,
    ) {
        if (clientOptions.remove(option)) {
            events += reply(WONT, option)
        }
    }

    private fun handleSubnegotiation(events: MutableList<TelnetEvent>) {
        if (subnegotiation.isEmpty()) return
        val option = subnegotiation[0].toInt() and 0xFF
        if (option == GMCP && GMCP in serverOptions) {
            // "Package.Message" then, after one space, the JSON; either half may be all there is.
            val payload = ByteArray(subnegotiation.size - 1) { subnegotiation[it + 1] }.decodeToString()
            val space = payload.indexOf(' ')
            val name = if (space < 0) payload else payload.substring(0, space)
            val data = if (space < 0) "" else payload.substring(space + 1).trim()
            if (name.isNotEmpty()) events += TelnetEvent.Gmcp(name, data)
            return
        }
        if (option == TTYPE && TTYPE in clientOptions && subnegotiation.size >= 2 &&
            subnegotiation[1].toInt() == TTYPE_SEND
        ) {
            val name = TERMINAL_TYPES[terminalTypeIndex]
            if (terminalTypeIndex < TERMINAL_TYPES.lastIndex) terminalTypeIndex++
            events +=
                TelnetEvent.Reply(
                    byteArrayOf(IAC.toByte(), SB.toByte(), TTYPE.toByte(), TTYPE_IS.toByte()) +
                        name.encodeToByteArray() +
                        byteArrayOf(IAC.toByte(), SE.toByte()),
                )
        }
    }

    private fun reply(
        command: Int,
        option: Int,
    ): TelnetEvent.Reply = TelnetEvent.Reply(byteArrayOf(IAC.toByte(), command.toByte(), option.toByte()))

    private fun appendData(b: Byte) {
        if (dataLength == data.size) data = data.copyOf(data.size * 2)
        data[dataLength++] = b
    }

    private fun flushData(events: MutableList<TelnetEvent>) {
        if (dataLength == 0) return
        events += TelnetEvent.Data(data.copyOf(dataLength))
        dataLength = 0
    }

    companion object {
        const val IAC = 255
        const val DONT = 254
        const val DO = 253
        const val WONT = 252
        const val WILL = 251
        const val SB = 250
        const val GA = 249
        const val SE = 240
        const val EOR_COMMAND = 239
        const val NUL = 0

        const val ECHO = 1
        const val SGA = 3
        const val TTYPE = 24
        const val GMCP = 201

        // The option (negotiated) has the same name as the command (239) that then marks the prompts.
        const val EOR = 25
        const val NAWS = 31

        private const val TTYPE_IS = 0
        private const val TTYPE_SEND = 1

        // MTTS: 1 ANSI colour, 4 UTF-8, 8 256 colours, 256 true colour.
        private val TERMINAL_TYPES = listOf("WARLOCK", "XTERM-256COLOR", "MTTS 269")

        private const val CORE_HELLO = """{"client":"Warlock","version":"3"}"""

        // Char for Char.Vitals (and Char.MaxStats on the servers that split the maxima out);
        // Char.Items for the inventory, which says what the hands hold; Room for Room.Info, whose
        // exits drive the compass.
        private const val CORE_SUPPORTS = """["Char 1","Char.Items 1","Room 1"]"""

        /** The bytes of a GMCP message to the server: `IAC SB GMCP name SP data IAC SE`, with any IAC in the data escaped. */
        fun gmcpMessage(
            name: String,
            data: String,
        ): ByteArray {
            val payload = (if (data.isEmpty()) name else "$name $data").encodeToByteArray()
            val escaped = ArrayList<Byte>(payload.size + 8)
            escaped += IAC.toByte()
            escaped += SB.toByte()
            escaped += GMCP.toByte()
            for (b in payload) {
                escaped += b
                if (b == IAC.toByte()) escaped += b
            }
            escaped += IAC.toByte()
            escaped += SE.toByte()
            return escaped.toByteArray()
        }
    }
}
