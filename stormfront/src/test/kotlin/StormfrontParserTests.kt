/*
import kotlin.test.assertEquals
import org.junit.Test


class StormfrontParserTests {
    @Test fun typicalParser() {
        val inputStream = ANTLRInputStream("<tag1 id=\"1223\"> hello &gt; sproctor </tag1>")
        val lexer = StormfrontLexer(inputStream)
        val commonTokenStream = CommonTokenStream(lexer)
        val parser = StormfrontParser(commonTokenStream)
        val visitor = StormfrontNodeVisitor()
        val document = visitor.visitDocument(parser.document())
        assertEquals(0, parser.numberOfSyntaxErrors)
    }
}*/