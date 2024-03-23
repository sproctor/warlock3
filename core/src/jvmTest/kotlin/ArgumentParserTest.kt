import warlockfe.warlock3.core.util.findArgumentBreak
import warlockfe.warlock3.core.util.parseArguments
import kotlin.test.Test
import kotlin.test.assertEquals

class ArgumentParserTest {
    @Test
    fun parseArguments_plain() {
        assertEquals(listOf("foo", "bar", "baz"), parseArguments("foo bar baz"), )
    }

    @Test
    fun parseArguments_quotedStrings() {
        assertEquals(listOf("foo bar", "baz"), parseArguments("\"foo bar\" baz"))
    }

    @Test
    fun parseArguments_escapedQuotesAndSpaces() {
        assertEquals(listOf("foo bar", "baz\" zip", "zoom"), parseArguments("foo\\ bar \"baz\\\" zip\" zoom"))
    }

    @Test
    fun argumentBreak_plain() {
        assertEquals(3, findArgumentBreak("foo bar baz"))
    }

    @Test
    fun argumentBreak_quotedStrings() {
        assertEquals(9, findArgumentBreak("\"foo bar\" baz"))
    }

    @Test
    fun argumentBreak_escapedQuotesAndSpaces() {
        assertEquals(8, findArgumentBreak("foo\\ bar \"baz\\\" zip\" zoom"))
    }
}