import warlockfe.warlock3.telnet.protocol.TelnetDecoder
import warlockfe.warlock3.telnet.protocol.TelnetDecoder.Companion.DO
import warlockfe.warlock3.telnet.protocol.TelnetDecoder.Companion.DONT
import warlockfe.warlock3.telnet.protocol.TelnetDecoder.Companion.ECHO
import warlockfe.warlock3.telnet.protocol.TelnetDecoder.Companion.EOR
import warlockfe.warlock3.telnet.protocol.TelnetDecoder.Companion.EOR_COMMAND
import warlockfe.warlock3.telnet.protocol.TelnetDecoder.Companion.GA
import warlockfe.warlock3.telnet.protocol.TelnetDecoder.Companion.GMCP
import warlockfe.warlock3.telnet.protocol.TelnetDecoder.Companion.IAC
import warlockfe.warlock3.telnet.protocol.TelnetDecoder.Companion.NAWS
import warlockfe.warlock3.telnet.protocol.TelnetDecoder.Companion.SB
import warlockfe.warlock3.telnet.protocol.TelnetDecoder.Companion.SE
import warlockfe.warlock3.telnet.protocol.TelnetDecoder.Companion.TTYPE
import warlockfe.warlock3.telnet.protocol.TelnetDecoder.Companion.WILL
import warlockfe.warlock3.telnet.protocol.TelnetDecoder.Companion.WONT
import warlockfe.warlock3.telnet.protocol.TelnetEvent
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class TelnetDecoderTests {
    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    private fun text(s: String): TelnetEvent.Data = TelnetEvent.Data(s.encodeToByteArray())

    private fun reply(vararg values: Int): TelnetEvent.Reply = TelnetEvent.Reply(bytes(*values))

    @Test
    fun plainTextPassesThrough() {
        val events = TelnetDecoder().decode("Hello\r\n".encodeToByteArray())
        assertEquals(listOf(text("Hello\r\n")), events)
    }

    @Test
    fun escapedIacIsOneDataByte() {
        val events = TelnetDecoder().decode(bytes('a'.code, IAC, IAC, 'b'.code))
        assertEquals(listOf(TelnetEvent.Data(bytes('a'.code, 0xFF, 'b'.code))), events)
    }

    @Test
    fun nulIsDropped() {
        val events = TelnetDecoder().decode(bytes('a'.code, '\r'.code, 0, 'b'.code))
        assertEquals(listOf(text("a\rb")), events)
    }

    @Test
    fun goAheadAndEndOfRecordMarkPrompts() {
        val decoder = TelnetDecoder()
        assertEquals(listOf(text("> "), TelnetEvent.Prompt), decoder.decode("> ".encodeToByteArray() + bytes(IAC, GA)))
        assertEquals(listOf(text("HP:10> "), TelnetEvent.Prompt), decoder.decode("HP:10> ".encodeToByteArray() + bytes(IAC, EOR_COMMAND)))
    }

    @Test
    fun serverEchoIsAcceptedAndReported() {
        val decoder = TelnetDecoder()
        assertEquals(
            listOf(reply(IAC, DO, ECHO), TelnetEvent.Echo(serverEcho = true)),
            decoder.decode(bytes(IAC, WILL, ECHO)),
        )
        // Asked again: already on, nothing to say (no negotiation loop).
        assertEquals(emptyList(), decoder.decode(bytes(IAC, WILL, ECHO)))
        assertEquals(
            listOf(reply(IAC, DONT, ECHO), TelnetEvent.Echo(serverEcho = false)),
            decoder.decode(bytes(IAC, WONT, ECHO)),
        )
        // Never on, so a WONT needs no answer.
        assertEquals(emptyList(), decoder.decode(bytes(IAC, WONT, ECHO)))
    }

    @Test
    fun endOfRecordOptionIsAccepted() {
        assertEquals(listOf(reply(IAC, DO, EOR)), TelnetDecoder().decode(bytes(IAC, WILL, EOR)))
    }

    @Test
    fun compressionAndOtherServerOptionsAreRefused() {
        val mccp2 = 86
        val msdp = 69
        val decoder = TelnetDecoder()
        assertEquals(listOf(reply(IAC, DONT, mccp2)), decoder.decode(bytes(IAC, WILL, mccp2)))
        assertEquals(listOf(reply(IAC, DONT, msdp)), decoder.decode(bytes(IAC, WILL, msdp)))
    }

    @Test
    fun windowSizeAndOtherClientOptionsAreRefused() {
        assertEquals(listOf(reply(IAC, WONT, NAWS)), TelnetDecoder().decode(bytes(IAC, DO, NAWS)))
    }

    @Test
    fun terminalTypeIsOfferedThenNamedInOrder() {
        val decoder = TelnetDecoder()
        assertEquals(listOf(reply(IAC, WILL, TTYPE)), decoder.decode(bytes(IAC, DO, TTYPE)))
        val send = bytes(IAC, SB, TTYPE, 1, IAC, SE)

        fun isReply(name: String) = TelnetEvent.Reply(bytes(IAC, SB, TTYPE, 0) + name.encodeToByteArray() + bytes(IAC, SE))
        assertEquals(listOf(isReply("WARLOCK")), decoder.decode(send))
        assertEquals(listOf(isReply("XTERM-256COLOR")), decoder.decode(send))
        assertEquals(listOf(isReply("MTTS 269")), decoder.decode(send))
        // Repeating the last answer is how the list is ended.
        assertEquals(listOf(isReply("MTTS 269")), decoder.decode(send))
    }

    @Test
    fun terminalTypeIsNotSentWhenNotNegotiated() {
        assertEquals(emptyList(), TelnetDecoder().decode(bytes(IAC, SB, TTYPE, 1, IAC, SE)))
    }

    @Test
    fun commandsSplitAcrossReadsAreReassembled() {
        val decoder = TelnetDecoder()
        assertEquals(listOf(text("ab")), decoder.decode(bytes('a'.code, 'b'.code, IAC)))
        assertEquals(emptyList(), decoder.decode(bytes(WILL)))
        assertEquals(
            listOf(reply(IAC, DO, ECHO), TelnetEvent.Echo(serverEcho = true), text("c")),
            decoder.decode(bytes(ECHO, 'c'.code)),
        )
    }

    @Test
    fun subnegotiationSplitAcrossReadsIsReassembled() {
        val decoder = TelnetDecoder()
        decoder.decode(bytes(IAC, DO, TTYPE))
        assertEquals(emptyList(), decoder.decode(bytes(IAC, SB, TTYPE)))
        assertEquals(emptyList(), decoder.decode(bytes(1, IAC)))
        assertEquals(
            listOf(TelnetEvent.Reply(bytes(IAC, SB, TTYPE, 0) + "WARLOCK".encodeToByteArray() + bytes(IAC, SE))),
            decoder.decode(bytes(SE)),
        )
    }

    @Test
    fun dataAroundCommandsKeepsItsOrder() {
        val events = TelnetDecoder().decode("a".encodeToByteArray() + bytes(IAC, WILL, ECHO) + "b".encodeToByteArray())
        assertEquals(
            listOf(text("a"), reply(IAC, DO, ECHO), TelnetEvent.Echo(serverEcho = true), text("b")),
            events,
        )
    }

    @Test
    fun gmcpIsAcceptedWithHelloAndSupports() {
        val events = TelnetDecoder().decode(bytes(IAC, WILL, GMCP))
        assertEquals(reply(IAC, DO, GMCP), events[0])
        assertEquals(
            TelnetEvent.Reply(TelnetDecoder.gmcpMessage("Core.Hello", """{"client":"Warlock","version":"3"}""")),
            events[1],
        )
        assertEquals(
            TelnetEvent.Reply(TelnetDecoder.gmcpMessage("Core.Supports.Set", """["Char 1","Char.Items 1","Room 1"]""")),
            events[2],
        )
        assertEquals(TelnetEvent.Reply(TelnetDecoder.gmcpMessage("Char.Items.Inv", "")), events[3])
        assertEquals(4, events.size)
    }

    @Test
    fun gmcpMessageIsFramedAndEscaped() {
        val message = TelnetDecoder.gmcpMessage("Core.Hello", "{}")
        assertContentEquals(
            bytes(IAC, SB, GMCP) + "Core.Hello {}".encodeToByteArray() + bytes(IAC, SE),
            message,
        )
        // UTF-8 never produces an IAC byte, so the escaping only matters for a raw 0xFF.
        val raw = TelnetDecoder.gmcpMessage("X", "")
        assertContentEquals(bytes(IAC, SB, GMCP) + "X".encodeToByteArray() + bytes(IAC, SE), raw)
    }

    @Test
    fun gmcpSubnegotiationBecomesAnEvent() {
        val decoder = TelnetDecoder()
        decoder.decode(bytes(IAC, WILL, GMCP))
        val payload = """Char.Vitals {"hp":"10","maxhp":"20"}""".encodeToByteArray()
        assertEquals(
            listOf(TelnetEvent.Gmcp("Char.Vitals", """{"hp":"10","maxhp":"20"}""")),
            decoder.decode(bytes(IAC, SB, GMCP) + payload + bytes(IAC, SE)),
        )
        // A message with no data, and one whose data holds an escaped IAC.
        assertEquals(
            listOf(TelnetEvent.Gmcp("Core.Ping", "")),
            decoder.decode(bytes(IAC, SB, GMCP) + "Core.Ping".encodeToByteArray() + bytes(IAC, SE)),
        )
    }

    @Test
    fun gmcpIsIgnoredWhenNotNegotiated() {
        val payload = "Char.Vitals {}".encodeToByteArray()
        assertEquals(emptyList(), TelnetDecoder().decode(bytes(IAC, SB, GMCP) + payload + bytes(IAC, SE)))
    }

    @Test
    fun unknownTwoByteCommandIsDropped() {
        val nop = 241
        assertEquals(listOf(text("ab")), TelnetDecoder().decode(bytes('a'.code, IAC, nop, 'b'.code)))
    }
}
