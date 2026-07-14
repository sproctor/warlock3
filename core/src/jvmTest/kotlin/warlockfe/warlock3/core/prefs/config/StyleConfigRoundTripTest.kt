package warlockfe.warlock3.core.prefs.config

import dev.eav.tomlkt.Toml
import warlockfe.warlock3.core.text.Background
import warlockfe.warlock3.core.text.FontConfig
import warlockfe.warlock3.core.text.StyleLayer
import warlockfe.warlock3.core.text.WarlockColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the TOML serialization of the style-bearing config sections (presets, windows, names) as the
 * appearance-model schema grows (per-item font + explicit weight). Verifies the additions stay lossless
 * and backward compatible, and that a plain bold preset does not gain the new fields on disk. Mirrors
 * [HighlightStyleInlineTest].
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
                    NameConfig(id = "n2", text = "Lyrena", textColorRef = "speech"),
                ),
            presets =
                mapOf(
                    "speech" to PresetStyleConfig(textColor = WarlockColor(red = 0, green = 200, blue = 255)),
                    "bold" to PresetStyleConfig(bold = true),
                    // Exercise the new per-item font + explicit-weight fields.
                    "heavy" to PresetStyleConfig(weight = 500, fontFamily = "Serif", fontSize = 14f),
                    // A skin-referenced color (tracks the skin).
                    "skinned" to PresetStyleConfig(textColorRef = "roomName", backgroundColorRef = "roomNameBg"),
                ),
            windows =
                mapOf(
                    "thoughts" to
                        WindowStyleConfig(
                            textColor = WarlockColor(red = 200, green = 200, blue = 200),
                            font = FontConfig(family = "Menlo", size = 13f, weight = 400),
                            nameFilter = true,
                            italic = true,
                        ),
                    "combat" to WindowStyleConfig(textColorRef = "creature", backgroundColorRef = "creatureBg"),
                ),
            settings =
                CharacterSettingsConfig(
                    defaultFont = FontConfig(family = "Helvetica", size = 15f, weight = 700),
                    defaultTextColor = WarlockColor(red = 220, green = 220, blue = 220),
                    defaultItalic = true,
                ),
        )

    @Test
    fun `style config round-trips including the new font and weight fields`() {
        val text = toml.encodeToString(CharacterConfig.serializer(), sample)
        assertEquals(sample, toml.decodeFromString(CharacterConfig.serializer(), text))
    }

    @Test
    fun `files written before the new fields still parse`() {
        // A presets/names block from before weight/fontFamily/fontSize existed must still decode, with
        // the new fields defaulting to inherit (null).
        val oldForm =
            """
            character = "global"

            [presets.bold]
            textColor = "default"
            backgroundColor = "default"
            bold = true
            italic = false
            underline = false
            monospace = false
            """.trimIndent()
        val decoded = toml.decodeFromString(CharacterConfig.serializer(), oldForm)
        val preset = decoded.presets.getValue("bold")
        assertEquals(true, preset.bold)
        assertEquals(null, preset.weight)
        assertEquals(null, preset.fontFamily)
        assertEquals(null, preset.fontSize)
    }

    @Test
    fun `a preset color ref survives the StyleLayer mappers`() {
        val config = PresetStyleConfig(textColorRef = "roomName", backgroundColorRef = "roomNameBg")
        val layer = config.toStyleLayer()
        assertEquals("roomName", layer.textColorRef)
        assertEquals("roomNameBg", layer.backgroundRef)
        assertEquals(config, layer.toPresetStyleConfig())
    }

    @Test
    fun `a base color ref survives the base-style mappers`() {
        val config = CharacterSettingsConfig(defaultTextColorRef = "default")
        assertEquals("default", config.toBaseStyleLayer().textColorRef)
        assertEquals("default", config.applyBaseStyle(config.toBaseStyleLayer()).defaultTextColorRef)
    }

    @Test
    fun `a bold-only preset does not emit the new font fields`() {
        // The nullable additions are dropped when unset (explicitNulls = false), so an existing bold
        // preset stays byte-stable rather than gaining weight/font lines.
        val text =
            toml.encodeToString(
                CharacterConfig.serializer(),
                CharacterConfig(
                    presets =
                        mapOf("bold" to PresetStyleConfig(bold = true)),
                ),
            )
        assertTrue("bold = true" in text)
        assertFalse("weight" in text, "a bold-only preset must not write a weight field")
        assertFalse("fontFamily" in text, "a bold-only preset must not write a fontFamily field")
    }

    @Test
    fun `an explicit heavy weight renders bold during the transition`() {
        assertEquals(true, PresetStyleConfig(weight = 700).toStyleDefinition().bold)
        assertEquals(true, HighlightStyleConfig(weight = 600).toStyleDefinition().bold)
        assertEquals(false, PresetStyleConfig(weight = 400).toStyleDefinition().bold)
    }

    @Test
    fun `preset config round-trips through StyleLayer with weight and font`() {
        val config =
            PresetStyleConfig(
                textColor = WarlockColor(red = 10, green = 20, blue = 30),
                weight = 500,
                fontFamily = "Serif",
                fontSize = 14f,
                italic = true,
            )
        assertEquals(config, config.toStyleLayer().toPresetStyleConfig())
    }

    @Test
    fun `a bold layer canonicalizes to the bold flag with no weight`() {
        val config = StyleLayer(weight = 700).toPresetStyleConfig()
        assertEquals(true, config.bold)
        assertEquals(null, config.weight)
    }

    @Test
    fun `a None background round-trips through the transparent sentinel`() {
        val config = StyleLayer(background = Background.None).toPresetStyleConfig()
        assertEquals(WarlockColor(0L), config.backgroundColor)
        assertEquals(Background.None, config.toStyleLayer().background)
    }

    @Test
    fun `the base style assembles from and writes back to the settings config`() {
        // defaultFont carries the font/weight half; the colors + italic/underline are the other half.
        val settings =
            CharacterSettingsConfig(
                defaultTextColor = WarlockColor(red = 10, green = 20, blue = 30),
                defaultFont = FontConfig(family = "Serif", size = 14f, weight = 700),
                defaultItalic = true,
            )
        val layer = settings.toBaseStyleLayer()
        assertEquals(WarlockColor(red = 10, green = 20, blue = 30), layer.textColor)
        assertEquals("Serif", layer.fontFamily)
        assertEquals(700, layer.weight)
        assertEquals(true, layer.italic)
        // Writing the assembled layer back onto a blank config reproduces the base fields (font preserved).
        assertEquals(settings, CharacterSettingsConfig().applyBaseStyle(layer))
    }
}
