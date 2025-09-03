/*
import kotlin.test.assertEquals
import org.junit.Test


class WraythParserTests {
    @Test fun typicalParser() {
        val inputStream = ANTLRInputStream("<tag1 id=\"1223\"> hello &gt; sproctor </tag1>")
        val lexer = WraythLexer(inputStream)
        val commonTokenStream = CommonTokenStream(lexer)
        val parser = WraythParser(commonTokenStream)
        val visitor = WraythNodeVisitor()
        val document = visitor.visitDocument(parser.document())
        assertEquals(0, parser.numberOfSyntaxErrors)
    }
}*/