package warlockfe.warlock3.core.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContrastTest {
    private val black = WarlockColor(red = 0, green = 0, blue = 0)
    private val white = WarlockColor(red = 255, green = 255, blue = 255)

    @Test
    fun blackOnWhiteIsMaxContrast() {
        assertEquals(21.0, contrastRatio(black, white), 0.1)
    }

    @Test
    fun identicalColorsAreOneToOne() {
        val gray = WarlockColor(red = 128, green = 128, blue = 128)
        assertEquals(1.0, contrastRatio(gray, gray), 0.001)
    }

    @Test
    fun ratioIsSymmetric() {
        assertEquals(contrastRatio(black, white), contrastRatio(white, black), 0.0001)
    }

    @Test
    fun paleYellowOnNearBlackIsHighContrast() {
        // The in-game case that looks broken on a light settings panel but is fine on the window bg.
        val paleYellow = WarlockColor(red = 240, green = 240, blue = 160)
        val nearBlack = WarlockColor(red = 30, green = 31, blue = 34)
        assertTrue(contrastRatio(paleYellow, nearBlack) > 3.0)
    }

    @Test
    fun textNearlyEqualBackgroundWarns() {
        val a = WarlockColor(red = 40, green = 40, blue = 48)
        val b = WarlockColor(red = 44, green = 44, blue = 52)
        assertTrue(contrastRatio(a, b) < 3.0)
    }

    @Test
    fun whiteHasFullLuminanceAndBlackNone() {
        assertEquals(1.0, white.relativeLuminance(), 0.001)
        assertEquals(0.0, black.relativeLuminance(), 0.001)
    }
}
