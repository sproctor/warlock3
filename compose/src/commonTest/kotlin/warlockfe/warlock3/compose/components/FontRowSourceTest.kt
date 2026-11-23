package warlockfe.warlock3.compose.components

import warlockfe.warlock3.core.text.StyleLayer
import warlockfe.warlock3.core.text.StyleScope
import warlockfe.warlock3.core.text.resolveSourced
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FontRowSourceTest {
    private fun source(
        character: StyleLayer,
        global: StyleLayer = StyleLayer(),
    ) = fontRowSource(
        resolveSourced(listOf(StyleScope.CHARACTER to character, StyleScope.GLOBAL to global)),
        StyleScope.CHARACTER,
    )

    @Test
    fun aSizeSetHereCountsAsSetHere() {
        // No family anywhere, so the family's source alone would leave Reset disabled.
        assertEquals(StyleScope.CHARACTER, source(StyleLayer(fontSize = 14f)))
    }

    @Test
    fun aSizeSetHereWinsOverAFamilyFromBelow() {
        assertEquals(StyleScope.CHARACTER, source(StyleLayer(fontSize = 14f), StyleLayer(fontFamily = "Serif")))
    }

    @Test
    fun aFamilySetHereCountsAsSetHere() {
        assertEquals(StyleScope.CHARACTER, source(StyleLayer(fontFamily = "Serif")))
    }

    @Test
    fun withNothingSetHereTheFamilysSourceIsShown() {
        assertEquals(StyleScope.GLOBAL, source(StyleLayer(), StyleLayer(fontFamily = "Serif", fontSize = 12f)))
    }

    @Test
    fun withNoFamilyTheSizesSourceIsShown() {
        assertEquals(StyleScope.GLOBAL, source(StyleLayer(), StyleLayer(fontSize = 12f)))
    }

    @Test
    fun nothingSetAnywhereIsTheDefault() {
        assertNull(source(StyleLayer()))
    }
}
