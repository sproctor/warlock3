import warlockfe.warlock3.telnet.protocol.StreamingTextDecoder
import kotlin.test.Test
import kotlin.test.assertEquals

class StreamingTextDecoderTests {
    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun asciiPassesThrough() {
        assertEquals("Hello, world", StreamingTextDecoder().decode("Hello, world".encodeToByteArray()))
    }

    @Test
    fun utf8IsDecoded() {
        assertEquals("café ☃", StreamingTextDecoder().decode("café ☃".encodeToByteArray()))
    }

    @Test
    fun utf8SequenceSplitAcrossReadsIsReassembled() {
        val decoder = StreamingTextDecoder()
        val snowman = "☃".encodeToByteArray() // E2 98 83
        assertEquals("a", decoder.decode(byteArrayOf('a'.code.toByte(), snowman[0])))
        assertEquals("", decoder.decode(byteArrayOf(snowman[1])))
        assertEquals("☃b", decoder.decode(byteArrayOf(snowman[2], 'b'.code.toByte())))
    }

    @Test
    fun latin1AccentFollowedByAsciiIsLatin1() {
        // E9 looks like the start of a three-byte sequence, but 'e' is not a continuation byte.
        assertEquals("cafée", StreamingTextDecoder().decode(bytes('c'.code, 'a'.code, 'f'.code, 0xE9, 'e'.code)))
    }

    @Test
    fun latin1AccentAtEndOfStreamComesOutOnFlush() {
        val decoder = StreamingTextDecoder()
        assertEquals("caf", decoder.decode(bytes('c'.code, 'a'.code, 'f'.code, 0xE9)))
        assertEquals("é", decoder.flush())
        assertEquals("", decoder.flush())
    }

    @Test
    fun loneContinuationAndInvalidLeadBytesAreLatin1() {
        assertEquals("Àÿ", StreamingTextDecoder().decode(bytes(0x80, 0xC0, 0xFF)))
    }

    @Test
    fun overlongSequenceFallsBackToLatin1() {
        // E0 80 80 has the right shape for three bytes but encodes U+0000 the long way.
        assertEquals("à", StreamingTextDecoder().decode(bytes(0xE0, 0x80, 0x80)))
    }

    @Test
    fun mixedEncodingsInOneStream() {
        val decoder = StreamingTextDecoder()
        val input = "ü".encodeToByteArray() + bytes(0xFC) + " ok".encodeToByteArray()
        assertEquals("üü ok", decoder.decode(input))
    }
}
