package warlockfe.warlock3.compose.ui.window

import warlockfe.warlock3.compose.util.SAFE_DEFAULT_STYLE
import warlockfe.warlock3.core.text.Background
import warlockfe.warlock3.core.text.StyleDefinition
import warlockfe.warlock3.core.text.StyleLayer
import warlockfe.warlock3.core.text.WarlockColor
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The base ("default text") style cascade. The ordering is what matters here: the safety net has to
 * stay below the skin, or a skin's light default can never win and every window renders
 * light-on-dark whatever the theme.
 */
class BaseStyleCascadeTest {
    private val skinText = WarlockColor("#1E1F22")
    private val skinBackground = WarlockColor("#FFFFFF")

    private val lightSkin =
        mapOf(
            "default" to StyleDefinition(textColor = skinText, backgroundColor = skinBackground),
        )

    private fun cascade(
        charBase: StyleLayer = StyleLayer(),
        charLegacy: StyleDefinition? = null,
        globalBase: StyleLayer = StyleLayer(),
        globalLegacy: StyleDefinition? = null,
        skin: Map<String, StyleDefinition> = lightSkin,
    ) = resolveBaseStyle(charBase, charLegacy, globalBase, globalLegacy, skin)

    @Test
    fun theSkinDefaultBeatsTheSafetyNet() {
        val resolved = cascade()
        assertEquals(skinText, resolved.textColor)
        assertEquals(Background.Fill(skinBackground), resolved.background)
    }

    @Test
    fun theSafetyNetAppliesOnlyWithNoSkin() {
        val resolved = cascade(skin = emptyMap())
        assertEquals(SAFE_DEFAULT_STYLE.textColor, resolved.textColor)
        assertEquals(Background.Fill(SAFE_DEFAULT_STYLE.backgroundColor), resolved.background)
    }

    @Test
    fun aSkinWithoutADefaultPresetStillFallsBack() {
        val resolved = cascade(skin = mapOf("link" to StyleDefinition(textColor = WarlockColor("#2962FF"))))
        assertEquals(SAFE_DEFAULT_STYLE.textColor, resolved.textColor)
    }

    @Test
    fun theCharacterBaseBeatsTheSkin() {
        val chosen = WarlockColor("#123456")
        val resolved = cascade(charBase = StyleLayer(textColor = chosen))
        assertEquals(chosen, resolved.textColor)
        // Only the attribute the character set is overridden; the rest still comes from the skin.
        assertEquals(Background.Fill(skinBackground), resolved.background)
    }

    @Test
    fun theGlobalBaseBeatsTheSkinButLosesToTheCharacter() {
        val charColor = WarlockColor("#111111")
        val globalColor = WarlockColor("#222222")
        assertEquals(
            charColor,
            cascade(
                charBase = StyleLayer(textColor = charColor),
                globalBase = StyleLayer(textColor = globalColor),
            ).textColor,
        )
        assertEquals(globalColor, cascade(globalBase = StyleLayer(textColor = globalColor)).textColor)
    }

    @Test
    fun aLegacyDefaultPresetSitsUnderItsOwnScopesBase() {
        val baseColor = WarlockColor("#111111")
        val legacyColor = WarlockColor("#333333")
        assertEquals(
            baseColor,
            cascade(
                charBase = StyleLayer(textColor = baseColor),
                charLegacy = StyleDefinition(textColor = legacyColor),
            ).textColor,
        )
        // With no character base of its own, the legacy preset still beats global and the skin.
        assertEquals(
            legacyColor,
            cascade(
                charLegacy = StyleDefinition(textColor = legacyColor),
                globalBase = StyleLayer(textColor = WarlockColor("#222222")),
            ).textColor,
        )
    }

    @Test
    fun aSkinReferencedColourResolvesAgainstTheSkinPalette() {
        // "default" is a palette slot: the character tracks the skin rather than freezing a literal,
        // which is what lets the base flip with the theme.
        val resolved = cascade(charBase = StyleLayer(textColorRef = "default"))
        assertEquals(skinText, resolved.textColor)
    }
}
