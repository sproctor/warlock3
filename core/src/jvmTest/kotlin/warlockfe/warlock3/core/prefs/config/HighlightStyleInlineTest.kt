package warlockfe.warlock3.core.prefs.config

import dev.eav.tomlkt.Toml
import warlockfe.warlock3.core.text.WarlockColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Highlight styles are serialized as an inline array of inline tables
 * (`styles = [ { group = 0, ... } ]`) rather than a nested `[[highlights.styles]]` sub-section, via
 * `@TomlInline` on [HighlightConfig.styles]. These tests pin that output and verify the change is
 * lossless and backward compatible with files written in the old block form.
 */
class HighlightStyleInlineTest {
    // Same configuration the real CharacterConfigStore uses.
    private val toml =
        Toml {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    private val sample =
        CharacterConfig(
            character = "global",
            highlights =
                listOf(
                    HighlightConfig(
                        id = "11111111-1111-1111-1111-111111111111",
                        pattern = "ZZZ_apple",
                        ignoreCase = true,
                        styles = listOf(HighlightStyleConfig(group = 0, textColor = WarlockColor(red = 255, green = 0, blue = 0))),
                    ),
                    // A regex highlight with two capture groups -> two inline style tables on one line.
                    HighlightConfig(
                        id = "22222222-2222-2222-2222-222222222222",
                        pattern = "(\\w+) the (\\w+)",
                        isRegex = true,
                        styles =
                            listOf(
                                HighlightStyleConfig(group = 1, textColor = WarlockColor(red = 0, green = 255, blue = 0)),
                                HighlightStyleConfig(group = 2, bold = true),
                            ),
                    ),
                ),
        )

    @Test
    fun `styles serialize as inline tables`() {
        val text = toml.encodeToString(CharacterConfig.serializer(), sample)
        assertTrue("[[highlights.styles]]" !in text, "styles should be inline, not a sub-table")
        assertTrue(
            Regex("""styles = \[\s*\{""").containsMatchIn(text),
            "styles should be an inline array of inline tables",
        )
    }

    @Test
    fun `inline form round-trips`() {
        val text = toml.encodeToString(CharacterConfig.serializer(), sample)
        assertEquals(sample, toml.decodeFromString(CharacterConfig.serializer(), text))
    }

    @Test
    fun `still reads the old block-form sub-table`() {
        // What files on disk look like today (pre-inline). Must keep parsing unchanged so no data
        // migration is needed; the store simply rewrites them in inline form on the next save.
        val oldForm =
            """
            character = "global"

            [[highlights]]
            id = "11111111-1111-1111-1111-111111111111"
            pattern = "ZZZ_apple"
            ignoreCase = true

            [[highlights.styles]]
            group = 0
            textColor = "#ffff0000"
            """.trimIndent()
        val decoded = toml.decodeFromString(CharacterConfig.serializer(), oldForm)
        assertEquals(1, decoded.highlights.size)
        assertEquals("ZZZ_apple", decoded.highlights[0].pattern)
        assertEquals(1, decoded.highlights[0].styles.size)
        assertEquals(0, decoded.highlights[0].styles[0].group)
        assertEquals(WarlockColor(red = 255, green = 0, blue = 0), decoded.highlights[0].styles[0].textColor)
    }
}
