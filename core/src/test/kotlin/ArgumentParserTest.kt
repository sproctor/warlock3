import cc.warlock.warlock3.core.util.parseArguments
import org.junit.jupiter.api.Test
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
}