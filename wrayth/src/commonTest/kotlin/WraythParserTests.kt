import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import kotlin.test.assertEquals
import org.junit.Test
import warlockfe.warlock3.wrayth.parser.WraythLexer
import warlockfe.warlock3.wrayth.parser.WraythParser
import warlockfe.warlock3.wrayth.protocol.WraythNodeVisitor


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
}
