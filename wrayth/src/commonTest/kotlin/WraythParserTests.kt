import org.antlr.v4.kotlinruntime.CharStreams
import org.antlr.v4.kotlinruntime.CommonTokenStream
import warlockfe.warlock3.wrayth.parsers.generated.WraythLexer
import warlockfe.warlock3.wrayth.parsers.generated.WraythParser
import warlockfe.warlock3.wrayth.protocol.CharData
import warlockfe.warlock3.wrayth.protocol.Content
import warlockfe.warlock3.wrayth.protocol.EndElement
import warlockfe.warlock3.wrayth.protocol.StartElement
import warlockfe.warlock3.wrayth.protocol.WraythNodeVisitor
import kotlin.test.Test
import kotlin.test.assertEquals

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
    fun startTagAttributesAndText() {
        val content = parse("<a exist=\"123\" noun=\"orc\">an orc</a>")

        assertEquals(
            listOf(
                StartElement("a", mapOf("exist" to "123", "noun" to "orc")),
                CharData("an orc"),
                EndElement("a"),
            ),
            content,
        )
    }

    @Test
    fun emptyTagExpandsToStartAndEnd() {
        assertEquals(
            listOf(
                StartElement("prompt", mapOf("time" to "1234")),
                EndElement("prompt"),
            ),
            parse("<prompt time=\"1234\"/>"),
        )
    }

    @Test
    fun attributeWithoutValueUsesNameAsValue() {
        // `attribute : Name (EQUALS STRING)?` — a valueless attribute falls back to its name as value.
        assertEquals(
            StartElement("style", mapOf("id" to "ROOMNAME", "bold" to "bold")),
            parse("<style id=\"ROOMNAME\" bold/>").first(),
        )
    }

    @Test
    fun literalAngleBracketsInsideAttributeString() {
        // The lexer's STRING rule consumes `<` and `>` inside a quoted value, so they don't prematurely
        // close the tag. The game sends command links whose value contains angle brackets.
        val content = parse("<d cmd=\"look in <chest>\">look</d>")

        assertEquals(
            StartElement("d", mapOf("cmd" to "look in <chest>")),
            content.first(),
        )
        assertEquals(CharData("look"), content[1])
    }

    @Test
    fun escapedAngleBracketsInsideAttributeStringAreUnescaped() {
        assertEquals(
            StartElement("d", mapOf("cmd" to "go <north> & <south>")),
            parse("<d cmd=\"go &lt;north&gt; &amp; &lt;south&gt;\"/>").first(),
        )
    }

    @Test
    fun greaterThanInsideAttributeStringDoesNotCloseTag() {
        val content = parse("<a cmd=\"swing > orc\">x</a>")

        assertEquals(StartElement("a", mapOf("cmd" to "swing > orc")), content.first())
        assertEquals(CharData("x"), content[1])
        assertEquals(EndElement("a"), content[2])
    }

    @Test
    fun embeddedQuotesInAttributeStringAreRead() {
        // The STRING rule tolerates non-XML values with embedded quotes (it reads past an inner quote
        // as long as it isn't followed by `=` or `>`), e.g. the bracketed, quoted noun the game sends.
        val content = parse("<a noun=\" - [\"Kertigen's Honor\"]\">item</a>")

        assertEquals(
            StartElement("a", mapOf("noun" to " - [\"Kertigen's Honor\"]")),
            content.first(),
        )
        assertEquals(CharData("item"), content[1])
    }

    @Test
    fun embeddedSingleQuotesInAttributeStringAreRead() {
        // The STRING rule tolerates non-XML values with embedded quotes (it reads past an inner quote
        // as long as it isn't followed by `=` or `>`), e.g. the bracketed, quoted noun the game sends.
        val content = parse("<a noun=' - Kertigen's Honor'>item</a>")

        assertEquals(
            StartElement("a", mapOf("noun" to " - Kertigen's Honor")),
            content.first(),
        )
        assertEquals(CharData("item"), content[1])
    }

    @Test
    fun entityReferencesInTextAreUnescaped() {
        // References are their own nodes; concatenating the text yields the unescaped string.
        val content = parse("you see &lt;1&gt; &amp; more")
        val text = content.filterIsInstance<CharData>().joinToString("") { it.data }

        assertEquals("you see <1> & more", text)
    }

    @Test
    fun numericCharacterReferencesInTextAreUnescaped() {
        val decimal = parse("&#62;").filterIsInstance<CharData>().joinToString("") { it.data }
        val hex = parse("&#x3c;").filterIsInstance<CharData>().joinToString("") { it.data }

        assertEquals(">", decimal)
        assertEquals("<", hex)
    }

    @Test
    fun multipleSiblingElements() {
        assertEquals(
            listOf(
                StartElement("pushBold", emptyMap()),
                EndElement("pushBold"),
                CharData("You attack the "),
                StartElement("a", mapOf("noun" to "orc")),
                CharData("orc"),
                EndElement("a"),
                CharData("."),
            ),
            parse("<pushBold/>You attack the <a noun=\"orc\">orc</a>."),
        )
    }

    private fun parse(line: String): List<Content> {
        val lexer = WraythLexer(CharStreams.fromString(line))
        val parser = WraythParser(CommonTokenStream(lexer))
        val content = WraythNodeVisitor.visitDocument(parser.document())
        assertEquals(0, parser.numberOfSyntaxErrors, "unexpected syntax errors parsing: $line")
        return content
    }
}
