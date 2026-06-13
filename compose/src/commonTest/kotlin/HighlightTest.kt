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

    @Test
    fun multiWordLiteralDoesNotMatchWhenTokenMissing() {
        // "orc" is present but "greater" is not, so the multi-word literal must not match.
        assertEquals(emptyList(), matched("an orc appears", literal("greater orc")))
    }

    @Test
    fun multiWordLiteralDoesNotMatchOutOfOrderTokens() {
        // Both tokens are present but not adjacent in literal order: must not match.
        assertEquals(emptyList(), matched("an orc that is greater", literal("greater orc")))
    }

    @Test
    fun nonMatchingMultiWordHighlightsAmongManyAddNothing() {
        // The pre-filter must skip non-matching multi-word whole-word highlights without altering results.
        val many = (0 until 500).map { literal("greater word$it") } + literal("greater orc")
        assertEquals(listOf("greater orc"), matched("a greater orc", *many.toTypedArray()))
    }

    @Test
    fun indexPreservesOverlappingHighlightOrder() {
        // Two highlights cover overlapping ranges; the index must apply matches in original list order so
        // overlap resolution is unchanged. Both should appear, narrower first as configured.
        val wide = literal("greater orc")
        val narrow = literal("orc")
        assertEquals(listOf("greater orc", "orc"), matched("a greater orc", wide, narrow))
    }

    @Test
    fun matchesWhenProbeTokenSharedAcrossManyHighlights() {
        // Many highlights share the longest token "greater" (the probe), so they all land in the same
        // index bucket; the real match must still be found and confirmed among them.
        val many = (0 until 500).map { literal("greater thing$it") } + literal("greater orc")
        assertEquals(listOf("greater orc"), matched("a greater orc appears", *many.toTypedArray()))
    }
}
