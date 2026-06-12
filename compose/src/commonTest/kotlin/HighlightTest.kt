import androidx.compose.ui.text.AnnotatedString
import warlockfe.warlock3.compose.model.LiteralHighlight
import warlockfe.warlock3.compose.model.ViewHighlight
import warlockfe.warlock3.compose.util.highlight
import warlockfe.warlock3.core.text.StyleDefinition
import warlockfe.warlock3.core.text.WarlockColor
import kotlin.test.Test
import kotlin.test.assertEquals

class HighlightTest {
    private val style = StyleDefinition(textColor = WarlockColor(red = 255, green = 0, blue = 0))

    private fun literal(
        word: String,
        ignoreCase: Boolean = true,
        partialWord: Boolean = false,
    ): LiteralHighlight =
        LiteralHighlight(literal = word, matchPartialWord = partialWord, ignoreCase = ignoreCase, style = style, sound = null)

    // The substrings that ended up styled by the highlights.
    private fun matched(
        text: String,
        vararg highlights: ViewHighlight,
    ): List<String> =
        AnnotatedString(text)
            .highlight(highlights.toList())
            .text.spanStyles
            .map { text.substring(it.start, it.end) }

    @Test
    fun wholeWordMatches() {
        assertEquals(listOf("orc"), matched("an orc", literal("orc")))
    }

    @Test
    fun wholeWordDoesNotMatchSubstring() {
        assertEquals(emptyList(), matched("a category of orcs", literal("cat")))
    }

    @Test
    fun ignoreCaseMatchesDifferentCase() {
        assertEquals(listOf("Orc"), matched("an Orc", literal("orc", ignoreCase = true)))
    }

    @Test
    fun caseSensitiveDoesNotMatchDifferentCase() {
        assertEquals(emptyList(), matched("an Orc", literal("orc", ignoreCase = false)))
    }

    @Test
    fun partialWordMatchesSubstring() {
        assertEquals(listOf("cat"), matched("category", literal("cat", partialWord = true)))
    }

    @Test
    fun multiWordLiteralMatches() {
        assertEquals(listOf("greater orc"), matched("a greater orc appears", literal("greater orc")))
    }

    @Test
    fun matchesEveryOccurrence() {
        assertEquals(listOf("orc", "orc"), matched("orc versus orc", literal("orc")))
    }

    @Test
    fun nonMatchingHighlightsAmongManyAddNothing() {
        // The pre-filter must not change results: hundreds of non-matching whole-word highlights, one match.
        val many = (0 until 500).map { literal("word$it") } + literal("orc")
        assertEquals(listOf("orc"), matched("an orc", *many.toTypedArray()))
    }
}
