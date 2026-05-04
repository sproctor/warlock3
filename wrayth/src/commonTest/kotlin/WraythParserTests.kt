import org.antlr.v4.kotlinruntime.CharStreams
import org.antlr.v4.kotlinruntime.CommonTokenStream
import kotlin.test.assertEquals
import warlockfe.warlock3.wrayth.parsers.generated.WraythLexer
import warlockfe.warlock3.wrayth.parsers.generated.WraythParser
import warlockfe.warlock3.wrayth.protocol.WraythBackgroundEvent
import warlockfe.warlock3.wrayth.protocol.WraythNavEvent
import warlockfe.warlock3.wrayth.protocol.WraythNodeVisitor
import warlockfe.warlock3.wrayth.protocol.WraythProtocolHandler
import kotlin.test.Test

class WraythParserTests {
    @Test
    fun typicalParser() {
        val inputStream = CharStreams.fromString("<tag1 id=\"1223\"> hello &gt; sproctor </tag1>")
        val lexer = WraythLexer(inputStream)
        val tokens = CommonTokenStream(lexer)
        val parser = WraythParser(tokens)
        WraythNodeVisitor.visitDocument(parser.document())
        assertEquals(0, parser.numberOfSyntaxErrors)
    }

    @Test
    fun navTagIncludesRoomNumber() {
        val events = WraythProtocolHandler().parseLine("<nav rm=\"123\"/>")

        assertEquals(WraythNavEvent(roomNumber = "123"), events.first())
    }

    @Test
    fun backgroundTagIncludesWindowAndImage() {
        val events = WraythProtocolHandler().parseLine("<background window=\"main\" img=\"room.png\"/>")

        assertEquals(WraythBackgroundEvent(windowName = "main", image = "room.png"), events.first())
    }

    @Test
    fun emptyBackgroundTagClearsBackground() {
        val events = WraythProtocolHandler().parseLine("<background/>")

        assertEquals(WraythBackgroundEvent(windowName = null, image = null), events.first())
    }
}
