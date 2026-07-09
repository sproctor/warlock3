package warlockfe.warlock3.core.text

import kotlin.test.Test
import kotlin.test.assertEquals

class StyleResolveTest {
    private val red = WarlockColor(red = 255, green = 0, blue = 0)
    private val green = WarlockColor(red = 0, green = 255, blue = 0)

    @Test
    fun emptyStackResolvesToDefaults() {
        assertEquals(ResolvedStyle(), resolve(emptyList()))
    }

    @Test
    fun mostSpecificLayerWinsPerAttribute() {
        // character over global over skin, each attribute independently.
        val skin = StyleLayer(textColor = red, weight = 400, italic = true)
        val global = StyleLayer(textColor = green)
        val character = StyleLayer(underline = true)
        val resolved = resolve(listOf(character, global, skin))
        assertEquals(green, resolved.textColor) // global beats skin; character sets no color
        assertEquals(400, resolved.weight) // only skin sets it
        assertEquals(true, resolved.italic) // only skin
        assertEquals(true, resolved.underline) // only character
    }

    @Test
    fun triStateBackgroundNoneOverridesInheritedFill() {
        val skin = StyleLayer(background = Background.Fill(red))
        val character = StyleLayer(background = Background.None)
        assertEquals(Background.None, resolve(listOf(character, skin)).background)
        // Unset falls through to the fill below.
        assertEquals(Background.Fill(red), resolve(listOf(StyleLayer(background = Background.Unset), skin)).background)
    }

    @Test
    fun toLayerMapsFalseBooleansToUnsetSoResolveMatchesLegacyOr() {
        // "off" booleans and unspecified color must become null/Unset — the property that makes
        // first-set-wins equal the old boolean-OR merge.
        assertEquals(StyleLayer(), StyleDefinition(bold = false, italic = false, underline = false).toLayer())
        assertEquals(700, StyleDefinition(bold = true).toLayer().weight)
        assertEquals(Background.Fill(red), StyleDefinition(backgroundColor = red).toLayer().background)
    }

    @Test
    fun stackedNamedStylesPreserveBoldFromEitherLayer() {
        // The load-bearing edge from the design doc: a leaf carrying [speech, bold]. speech sets only a
        // color (bold=false -> weight unset), bold sets weight 700. First-set-wins must yield bold.
        val speech = StyleDefinition(textColor = red).toLayer()
        val bold = StyleDefinition(bold = true).toLayer()
        val resolved = resolve(listOf(speech, bold))
        assertEquals(red, resolved.textColor)
        assertEquals(700, resolved.weight)
        // Order independent for these disjoint attributes.
        assertEquals(resolve(listOf(bold, speech)), resolved)
    }

    @Test
    fun aMoreSpecificLayerCanTurnAnAttributeBackOff() {
        // New capability the tri-state model unlocks: explicit false at the character layer beats an
        // inherited true. (The legacy adapter never produces this, but the type supports it.)
        val skin = StyleLayer(italic = true)
        val character = StyleLayer(italic = false)
        assertEquals(false, resolve(listOf(character, skin)).italic)
    }

    @Test
    fun styleDefinitionRoundTripsThroughLayerForRepresentableFields() {
        val original = StyleDefinition(textColor = red, backgroundColor = green, bold = true, italic = true, underline = true)
        assertEquals(original, resolve(listOf(original.toLayer())).toStyleDefinition())
    }

    @Test
    fun perItemFontResolves() {
        val skin = StyleLayer(fontFamily = "Serif", fontSize = 12f, weight = 400)
        val character = StyleLayer(fontSize = 16f)
        val resolved = resolve(listOf(character, skin))
        assertEquals("Serif", resolved.fontFamily)
        assertEquals(16f, resolved.fontSize)
        assertEquals(400, resolved.weight)
    }

    @Test
    fun resolveSourcedTagsEachAttributeWithItsLayer() {
        val stack =
            listOf(
                StyleScope.CHARACTER to StyleLayer(textColor = red),
                StyleScope.GLOBAL to StyleLayer(weight = 700),
                StyleScope.SKIN to StyleLayer(italic = true),
            )
        val sourced = resolveSourced(stack)
        assertEquals(Sourced(red, StyleScope.CHARACTER), sourced.textColor)
        assertEquals(Sourced(700, StyleScope.GLOBAL), sourced.weight)
        assertEquals(Sourced(true, StyleScope.SKIN), sourced.italic)
        // Nothing set underline -> no source, default value.
        assertEquals(Sourced(false, null), sourced.underline)
        assertEquals(Sourced(null, null), sourced.fontFamily)
    }

    @Test
    fun resolveSourcedTracksTheTriStateBackgroundSource() {
        val sourced =
            resolveSourced(
                listOf(
                    StyleScope.CHARACTER to StyleLayer(background = Background.None),
                    StyleScope.SKIN to StyleLayer(background = Background.Fill(red)),
                ),
            )
        assertEquals(Sourced<Background>(Background.None, StyleScope.CHARACTER), sourced.background)
    }
}
