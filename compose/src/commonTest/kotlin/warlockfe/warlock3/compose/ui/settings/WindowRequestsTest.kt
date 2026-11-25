package warlockfe.warlock3.compose.ui.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WindowRequestsTest {
    @Test
    fun anUnknownWindowNeedsAPendingRow() {
        assertTrue(needsPendingRow("familiar", savedNames = setOf("room"), announcedNames = setOf("inv")))
    }

    @Test
    fun aSavedWindowHasARowAlready() {
        assertFalse(needsPendingRow("room", savedNames = setOf("room"), announcedNames = emptySet()))
    }

    @Test
    fun anAnnouncedWindowHasARowAlready() {
        assertFalse(needsPendingRow("inv", savedNames = emptySet(), announcedNames = setOf("inv")))
    }

    @Test
    fun mainNeverNeedsAPendingRowEvenWithNothingSaved() {
        assertFalse(needsPendingRow("main", savedNames = emptySet(), announcedNames = emptySet()))
    }

    @Test
    fun aRequestForAHiddenWindowOpensOtherWindowsAndIsSettled() {
        val outcome =
            settleRevealRequests(
                requests = listOf("inv"),
                hiddenNames = setOf("inv"),
                savedNames = emptySet(),
                announcedNames = setOf("inv"),
            )
        assertEquals(RevealOutcome(openOtherWindows = true, unsettled = emptyList()), outcome)
    }

    @Test
    fun aRequestForASavedWindowIsSettledWithoutOpeningOtherWindows() {
        val outcome =
            settleRevealRequests(
                requests = listOf("room"),
                hiddenNames = setOf("inv"),
                savedNames = setOf("room"),
                announcedNames = setOf("room", "inv"),
            )
        assertEquals(RevealOutcome(openOtherWindows = false, unsettled = emptyList()), outcome)
    }

    @Test
    fun anUnknownRequestWaitsForTheGameToAnnounceIt() {
        val requests = listOf("inv")
        val before = settleRevealRequests(requests, hiddenNames = emptySet(), savedNames = emptySet(), announcedNames = emptySet())
        assertEquals(RevealOutcome(openOtherWindows = false, unsettled = listOf("inv")), before)

        val after = settleRevealRequests(requests, hiddenNames = setOf("inv"), savedNames = emptySet(), announcedNames = setOf("inv"))
        assertEquals(RevealOutcome(openOtherWindows = true, unsettled = emptyList()), after)
    }

    @Test
    fun aRequestForMainIsSettled() {
        val outcome = settleRevealRequests(listOf("main"), hiddenNames = emptySet(), savedNames = emptySet(), announcedNames = emptySet())
        assertEquals(RevealOutcome(openOtherWindows = false, unsettled = emptyList()), outcome)
    }
}
