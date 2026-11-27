package warlockfe.warlock3.compose.ui.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WindowRequestsTest {
    @Test
    fun anUnsavedWindowShowsAPendingRow() {
        assertTrue(showsPendingRow("familiar", savedNames = setOf("room")))
    }

    @Test
    fun aSavedWindowNoLongerShowsAPendingRow() {
        assertFalse(showsPendingRow("room", savedNames = setOf("room")))
    }

    @Test
    fun mainNeverShowsAPendingRowEvenWithNothingSaved() {
        assertFalse(showsPendingRow("main", savedNames = emptySet()))
    }

    @Test
    fun requestsWaitWhileSettingsLoad() {
        // Before the load a saved window looks hidden; settling now would open Other windows for it.
        assertNull(settleRevealRequests(listOf("room"), settingsLoaded = false, hiddenNames = setOf("room"), savedNames = emptySet()))
    }

    @Test
    fun aRequestForAHiddenWindowOpensOtherWindows() {
        val outcome = settleRevealRequests(listOf("inv"), settingsLoaded = true, hiddenNames = setOf("inv"), savedNames = emptySet())
        assertEquals(RevealOutcome(openOtherWindows = true, newPending = emptyList()), outcome)
    }

    @Test
    fun aRequestForASavedWindowNeedsNothing() {
        val outcome = settleRevealRequests(listOf("room"), settingsLoaded = true, hiddenNames = setOf("inv"), savedNames = setOf("room"))
        assertEquals(RevealOutcome(openOtherWindows = false, newPending = emptyList()), outcome)
    }

    @Test
    fun aRequestForAnUnknownWindowBecomesAPendingRow() {
        val outcome =
            settleRevealRequests(listOf("familiar"), settingsLoaded = true, hiddenNames = setOf("inv"), savedNames = setOf("room"))
        assertEquals(RevealOutcome(openOtherWindows = false, newPending = listOf("familiar")), outcome)
    }

    @Test
    fun aRequestForMainNeedsNothing() {
        val outcome = settleRevealRequests(listOf("main"), settingsLoaded = true, hiddenNames = emptySet(), savedNames = emptySet())
        assertEquals(RevealOutcome(openOtherWindows = false, newPending = emptyList()), outcome)
    }
}
