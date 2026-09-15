import warlockfe.warlock3.telnet.ansi.AnsiColor
import warlockfe.warlock3.telnet.ansi.AnsiParser
import warlockfe.warlock3.telnet.ansi.AnsiSpan
import warlockfe.warlock3.telnet.ansi.AnsiStyle
import kotlin.test.Test
import kotlin.test.assertEquals

class AnsiParserTests {
    private val esc = "\u001B"

    private fun red() = AnsiStyle(foreground = AnsiColor.Indexed(1))

    @Test
    fun plainTextIsOneDefaultSpan() {
        assertEquals(listOf(AnsiSpan("Hello", AnsiStyle.Default)), AnsiParser().parse("Hello"))
    }

    @Test
    fun colorSequenceSplitsSpans() {
        val spans = AnsiParser().parse("a$esc[31mb$esc[0mc")
        assertEquals(
            listOf(
                AnsiSpan("a", AnsiStyle.Default),
                AnsiSpan("b", red()),
                AnsiSpan("c", AnsiStyle.Default),
            ),
            spans,
        )
    }

    @Test
    fun attributesAccumulateUntilReset() {
        val spans = AnsiParser().parse("$esc[1m$esc[31mbold red$esc[4mand underlined$esc[mplain")
        assertEquals(
            listOf(
                AnsiSpan("bold red", AnsiStyle(foreground = AnsiColor.Indexed(1), bold = true)),
                AnsiSpan("and underlined", AnsiStyle(foreground = AnsiColor.Indexed(1), bold = true, underline = true)),
                AnsiSpan("plain", AnsiStyle.Default),
            ),
            spans,
        )
    }

    @Test
    fun combinedParametersInOneSequence() {
        val spans = AnsiParser().parse("$esc[1;32;44mx")
        assertEquals(
            listOf(AnsiSpan("x", AnsiStyle(foreground = AnsiColor.Indexed(2), background = AnsiColor.Indexed(4), bold = true))),
            spans,
        )
    }

    @Test
    fun brightColorsAndAttributeOffs() {
        val spans = AnsiParser().parse("$esc[93;107;1;3;7mx$esc[22;23;27;39;49my")
        assertEquals(
            listOf(
                AnsiSpan(
                    "x",
                    AnsiStyle(
                        foreground = AnsiColor.Indexed(11),
                        background = AnsiColor.Indexed(15),
                        bold = true,
                        italic = true,
                        inverse = true,
                    ),
                ),
                AnsiSpan("y", AnsiStyle.Default),
            ),
            spans,
        )
    }

    @Test
    fun indexedAndTrueColor() {
        val spans = AnsiParser().parse("$esc[38;5;208ma$esc[48;2;10;20;30mb")
        assertEquals(
            listOf(
                AnsiSpan("a", AnsiStyle(foreground = AnsiColor.Indexed(208))),
                AnsiSpan("b", AnsiStyle(foreground = AnsiColor.Indexed(208), background = AnsiColor.Rgb(10, 20, 30))),
            ),
            spans,
        )
    }

    @Test
    fun colonSeparatedSubparameters() {
        val spans = AnsiParser().parse("$esc[38:5:196mx")
        assertEquals(listOf(AnsiSpan("x", AnsiStyle(foreground = AnsiColor.Indexed(196)))), spans)
    }

    @Test
    fun extendedColorWithMissingArgumentsChangesNothing() {
        val spans = AnsiParser().parse("$esc[31m$esc[38;5mx")
        assertEquals(listOf(AnsiSpan("x", red())), spans)
    }

    @Test
    fun emptyParameterIsZero() {
        val spans = AnsiParser().parse("$esc[31ma$esc[mb$esc[;1mc")
        assertEquals(
            listOf(
                AnsiSpan("a", red()),
                AnsiSpan("b", AnsiStyle.Default),
                AnsiSpan("c", AnsiStyle(bold = true)),
            ),
            spans,
        )
    }

    @Test
    fun sequenceSplitAcrossParseCallsIsReassembled() {
        val parser = AnsiParser()
        assertEquals(listOf(AnsiSpan("a", AnsiStyle.Default)), parser.parse("a$esc["))
        assertEquals(emptyList(), parser.parse("3"))
        assertEquals(listOf(AnsiSpan("b", red())), parser.parse("1mb"))
        // The style carries over to the next chunk too.
        assertEquals(listOf(AnsiSpan("c", red())), parser.parse("c"))
    }

    @Test
    fun otherControlSequencesAreDropped() {
        // Cursor movement, erase, a private-mode sequence, and an OSC title (both terminators).
        val input = "a$esc[2J$esc[10;20H$esc[?25lb$esc]0;title\u0007c$esc]2;title$esc\\d"
        assertEquals(listOf(AnsiSpan("abcd", AnsiStyle.Default)), AnsiParser().parse(input))
    }

    @Test
    fun csiWithIntermediateByteIsNotSgr() {
        assertEquals(listOf(AnsiSpan("x", AnsiStyle.Default)), AnsiParser().parse("$esc[ 31mx"))
    }

    @Test
    fun twoCharacterEscapesAreDropped() {
        assertEquals(listOf(AnsiSpan("ab", AnsiStyle.Default)), AnsiParser().parse("a$esc(B$esc=b"))
    }

    @Test
    fun controlCharactersOtherThanLineStructureAreDropped() {
        assertEquals(listOf(AnsiSpan("a\r\n\tb", AnsiStyle.Default)), AnsiParser().parse("a\r\n\t\u0007\bb"))
    }

    @Test
    fun unfinishedOscFollowedByAnotherEscapeIsRecovered() {
        val spans = AnsiParser().parse("$esc]0;never terminated$esc[31mx")
        assertEquals(listOf(AnsiSpan("x", red())), spans)
    }
}
