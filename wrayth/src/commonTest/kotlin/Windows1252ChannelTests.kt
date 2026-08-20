import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.test.runTest
import warlockfe.warlock3.wrayth.network.readWindows1252Available
import warlockfe.warlock3.wrayth.network.readWindows1252Line
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

// The reads every game connection goes through, whichever transport delivered the bytes: a TCP
// socket for NetworkSocket, WebSocket frames for WarlockWebSocket.
class Windows1252ChannelTests {
    private suspend fun channelOf(vararg bytes: Int) =
        ByteChannel(autoFlush = true).also { channel ->
            channel.writeFully(ByteArray(bytes.size) { bytes[it].toByte() })
            channel.close()
        }

    private suspend fun channelOf(text: String) = channelOf(*text.map { it.code }.toIntArray())

    @Test
    fun linesAreSplitOnEitherTerminatorOrBoth() =
        runTest {
            val channel = channelOf("lf\ncr\rcrlf\r\nlast")

            assertEquals("lf", channel.readWindows1252Line())
            assertEquals("cr", channel.readWindows1252Line())
            assertEquals("crlf", channel.readWindows1252Line())
            assertEquals("last", channel.readWindows1252Line())
        }

    @Test
    fun theEndOfTheStreamIsNullRatherThanAnEmptyLine() =
        runTest {
            val channel = channelOf("only line\n")

            assertEquals("only line", channel.readWindows1252Line())
            assertNull(channel.readWindows1252Line())
        }

    @Test
    fun anEmptyLineIsStillALine() =
        runTest {
            val channel = channelOf("\n\n")

            assertEquals("", channel.readWindows1252Line())
            assertEquals("", channel.readWindows1252Line())
            assertNull(channel.readWindows1252Line())
        }

    @Test
    fun aLineTheStreamEndsRightAfterACarriageReturnIsStillReturned() =
        runTest {
            // CR is only half a terminator until the next byte says otherwise, and here there is no
            // next byte: the peer hung up on the CR. The line before it is still a line.
            val channel = channelOf("text\r")

            assertEquals("text", channel.readWindows1252Line())
            assertNull(channel.readWindows1252Line())
        }

    @Test
    fun highBytesDecodeAsWindows1252RatherThanLatin1() =
        runTest {
            // 0x97 and 0x92 are an em dash and a right quote in windows-1252; in latin-1 - which is
            // what a byte-for-byte pass-through would give - they are unprintable controls.
            val channel = channelOf('a'.code, 0x97, 'b'.code, 0x92, 's'.code, '\n'.code)

            assertEquals("a—b’s", channel.readWindows1252Line())
        }

    @Test
    fun readAvailableTakesWhateverArrivedAndThenReportsTheEnd() =
        runTest {
            val channel = channelOf("half a li")
            val buffer = ByteArray(4096)

            assertEquals("half a li", channel.readWindows1252Available(buffer, min = 1))
            assertNull(channel.readWindows1252Available(buffer, min = 1))
        }
}
