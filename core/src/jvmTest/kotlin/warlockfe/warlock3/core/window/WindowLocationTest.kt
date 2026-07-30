package warlockfe.warlock3.core.window

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WindowLocationTest {
    @Test
    fun docksTheSideTheProtocolNames() {
        assertEquals(WindowLocation.LEFT, WindowLocation.fromProtocol("left"))
        assertEquals(WindowLocation.RIGHT, WindowLocation.fromProtocol("right"))
        assertEquals(WindowLocation.TOP, WindowLocation.fromProtocol("top"))
        assertEquals(WindowLocation.BOTTOM, WindowLocation.fromProtocol("bottom"))
    }

    @Test
    fun mainDocksRightBecauseWeHaveNoFloatingSlot() {
        // The protocol's "main" floats a panel over the main text area. MAIN is our main text window
        // itself, so a panel must never resolve to it.
        assertEquals(WindowLocation.RIGHT, WindowLocation.fromProtocol("main"))
    }

    @Test
    fun chromeRegionsDoNotDock() {
        // statBar and quickBar are drawn by the client, not docked as panels: the vitals bar reads
        // the minivitals panel directly, so docking it would show the same bars twice.
        assertNull(WindowLocation.fromProtocol("statBar"))
        assertNull(WindowLocation.fromProtocol("quickBar"))
    }

    @Test
    fun unspecifiedOrUnknownLocationDoesNotDock() {
        assertNull(WindowLocation.fromProtocol(null))
        assertNull(WindowLocation.fromProtocol(""))
        assertNull(WindowLocation.fromProtocol("somewhere else"))
    }

    @Test
    fun locationMatchingIgnoresCase() {
        assertEquals(WindowLocation.RIGHT, WindowLocation.fromProtocol("RIGHT"))
        assertNull(WindowLocation.fromProtocol("STATBAR"))
    }
}
