package warlockfe.warlock3.core.prefs.config

import dev.eav.tomlkt.Toml
import warlockfe.warlock3.core.text.FontConfig
import warlockfe.warlock3.core.text.WarlockColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the current TOML serialization of the style-bearing config sections (presets, windows, names)
 * so the appearance-model schema additions (weight/font/tri-state background) can prove they stay
 * lossless and backward compatible. Mirrors [HighlightStyleInlineTest].
 */
class StyleConfigRoundTripTest {
    private val toml =
        Toml {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    private val sample =
        CharacterConfig(
            character = "global",
            names =
                listOf(
                    NameConfig(
                        id = "n1",
                        text = "Sir Robyn",
                        textColor = WarlockColor(red = 0, green = 255, blue = 0),
                        bold = true,
                    ),
                ),
            presets =
                mapOf(
                    "speech" to PresetStyleConfig(textColor = WarlockColor(red = 0, green = 200, blue = 255)),
                    "bold" to PresetStyleConfig(bold = true),
                ),
            windows =
                mapOf(
                    "thoughts" to
                        WindowStyleConfig(
                            textColor = WarlockColor(red = 200, green = 200, blue = 200),
                            font = FontConfig(family = "Menlo", size = 13f, weight = 400),
                            nameFilter = true,
                        ),
                ),
        )

    @Test
    fun `style config round-trips`() {
        val text = toml.encodeToString(CharacterConfig.serializer(), sample)
        assertEquals(sample, toml.decodeFromString(CharacterConfig.serializer(), text))
    }

    @Test
    fun `a bold preset serializes with the bold flag`() {
        // Existing files express bold as `bold = true`. The weight-not-bold migration must keep writing
        // this form (not a `weight = 700`) for the common case so on-disk files stay byte-stable.
        val text =
            toml.encodeToString(
                CharacterConfig.serializer(),
                CharacterConfig(
                    presets =
                        mapOf("bold" to PresetStyleConfig(bold = true)),
                ),
            )
        assertTrue("bold = true" in text, "expected the bold preset to serialize as `bold = true`")
    }
}
