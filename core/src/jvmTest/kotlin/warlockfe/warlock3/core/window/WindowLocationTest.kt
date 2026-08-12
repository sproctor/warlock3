package warlockfe.warlock3.core.window

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WindowLocationTest {
    @Test
    fun readsEveryLocationTheRealClientAccepts() {
        // Anything outside this set comes back from Wrayth as "_error bad location in <the tag>".
        assertEquals(WindowLocation.LEFT, WindowLocation.fromProtocol("left"))
        assertEquals(WindowLocation.RIGHT, WindowLocation.fromProtocol("right"))
        assertEquals(WindowLocation.CENTER, WindowLocation.fromProtocol("center"))
        assertEquals(WindowLocation.DETACHED, WindowLocation.fromProtocol("detach"))
        assertEquals(WindowLocation.STATBAR, WindowLocation.fromProtocol("statBar"))
        assertEquals(WindowLocation.QUICKBAR, WindowLocation.fromProtocol("quickBar"))
    }

    @Test
    fun unspecifiedOrRejectedLocationCenters() {
        // Wrayth floats a window it was told nothing about, and one whose location it rejected, in
        // the same place: over the main text area, which is what center means. "main" is the
        // rejected one that turns up in practice - Lich panels (UberBar, CreatureWindow,
        // TreasureWindow) ask for it.
        assertEquals(WindowLocation.CENTER, WindowLocation.fromProtocol(null))
        assertEquals(WindowLocation.CENTER, WindowLocation.fromProtocol("main"))
        assertEquals(WindowLocation.CENTER, WindowLocation.fromProtocol("top"))
        assertEquals(WindowLocation.CENTER, WindowLocation.fromProtocol("bottom"))
        assertEquals(WindowLocation.CENTER, WindowLocation.fromProtocol(""))
        assertEquals(WindowLocation.CENTER, WindowLocation.fromProtocol("somewhere else"))
    }

    @Test
    fun locationMatchingIsExact() {
        // The protocol spells these exactly, camel case and all ("statBar", never "statusbar"),
        // so a miscased value is just another unrecognised one.
        assertEquals(WindowLocation.CENTER, WindowLocation.fromProtocol("RIGHT"))
        assertEquals(WindowLocation.CENTER, WindowLocation.fromProtocol("Center"))
    }

    @Test
    fun chromeBarsAreNotAPlaceInTheLayout() {
        // A panel bound for one of these is drawn as chrome and never becomes a window, so these
        // two must stay distinguishable from the center fallback the unrecognised values get.
        assertTrue(WindowLocation.STATBAR.isChrome)
        assertTrue(WindowLocation.QUICKBAR.isChrome)
        assertFalse(WindowLocation.CENTER.isChrome)
        assertFalse(WindowLocation.LEFT.isChrome)
        assertFalse(WindowLocation.RIGHT.isChrome)
        assertFalse(WindowLocation.DETACHED.isChrome)
        // The miscased spelling is not one of them: it is an error to the real client, so it lands
        // in the center fallback like any other value we do not recognise.
        assertFalse(WindowLocation.fromProtocol("statusbar").isChrome)
    }
}
