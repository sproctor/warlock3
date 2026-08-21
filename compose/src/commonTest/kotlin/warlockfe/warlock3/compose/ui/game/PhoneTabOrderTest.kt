package warlockfe.warlock3.compose.ui.game

import warlockfe.warlock3.core.window.WindowInfo
import warlockfe.warlock3.core.window.WindowLocation
import warlockfe.warlock3.core.window.WindowType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PhoneTabOrderTest {
    private fun window(
        name: String,
        title: String = name,
        type: WindowType = WindowType.STREAM,
        location: WindowLocation = WindowLocation.CENTER,
        resident: Boolean = true,
    ) = WindowInfo(
        name = name,
        title = title,
        subtitle = null,
        windowType = type,
        showTimestamps = false,
        backgroundImage = null,
        location = location,
        resident = resident,
    )

    private val main = window("main", title = "Story")
    private val thoughts = window("thoughts", title = "Thoughts")
    private val death = window("death", title = "Deaths")

    // --- tabbable -----------------------------------------------------------------------------

    @Test
    fun chromePanelsAreNotTabbable() {
        val statBar = window("statBar", type = WindowType.PANEL, location = WindowLocation.STATBAR)
        val quickBar = window("quickBar", type = WindowType.PANEL, location = WindowLocation.QUICKBAR)
        assertEquals(listOf(main), listOf(main, statBar, quickBar).tabbable())
    }

    @Test
    fun transientWindowsAreNotTabbable() {
        assertEquals(listOf(main), listOf(main, window("popup", resident = false)).tabbable())
    }

    // --- decoding -----------------------------------------------------------------------------

    @Test
    fun nothingSavedIsAuto() {
        assertIs<PhoneTabOrder.Auto>(decodePhoneTabOrder(null))
    }

    @Test
    fun unreadableValueFallsBackToAuto() {
        assertIs<PhoneTabOrder.Auto>(decodePhoneTabOrder("{\"not\":\"a list\"}"))
    }

    @Test
    fun emptyListIsExplicitNotAuto() {
        assertEquals(PhoneTabOrder.Explicit(emptyList()), decodePhoneTabOrder("[]"))
    }

    @Test
    fun repeatedNamesAreDropped() {
        // The strip keys its items by name, so a repeat would crash it rather than degrade.
        assertEquals(
            PhoneTabOrder.Explicit(listOf("main", "thoughts")),
            decodePhoneTabOrder("[\"main\",\"thoughts\",\"main\"]"),
        )
    }

    @Test
    fun orderRoundTrips() {
        val names = listOf("main", "thoughts", "a name, with a comma")
        assertEquals(PhoneTabOrder.Explicit(names), decodePhoneTabOrder(encodePhoneTabOrder(names)))
    }

    // --- reconciliation -----------------------------------------------------------------------

    @Test
    fun loadingShowsOnlyMain() {
        assertEquals(
            listOf("main"),
            reconcilePhoneTabs(PhoneTabOrder.Loading, listOf(main, thoughts)),
        )
    }

    @Test
    fun autoShowsEveryTabbableWindowWithMainFirst() {
        assertEquals(
            listOf("main", "death", "thoughts"),
            reconcilePhoneTabs(PhoneTabOrder.Auto, listOf(thoughts, death, main)),
        )
    }

    @Test
    fun autoAddsMainEvenBeforeTheGameAnnouncesIt() {
        assertEquals(listOf("main"), reconcilePhoneTabs(PhoneTabOrder.Auto, emptyList()))
    }

    @Test
    fun explicitDoesNotAdoptAWindowTheGameAnnounces() {
        assertEquals(
            listOf("main", "thoughts"),
            reconcilePhoneTabs(
                PhoneTabOrder.Explicit(listOf("main", "thoughts")),
                listOf(main, thoughts, death),
            ),
        )
    }

    @Test
    fun anAbsentWindowLeavesTheStripButNotTheSavedOrder() {
        val saved = PhoneTabOrder.Explicit(listOf("main", "thoughts", "death"))
        assertEquals(listOf("main", "death"), reconcilePhoneTabs(saved, listOf(main, death)))
        // The saved order is untouched, so the window returns to its old place, not to the end.
        assertEquals(
            listOf("main", "thoughts", "death"),
            reconcilePhoneTabs(saved, listOf(main, thoughts, death)),
        )
    }

    @Test
    fun mainIsForcedBackWhenMissing() {
        assertEquals(
            listOf("main", "thoughts"),
            reconcilePhoneTabs(PhoneTabOrder.Explicit(listOf("thoughts")), listOf(main, thoughts)),
        )
    }

    @Test
    fun mainKeepsItsPlaceWhenTheUserHasMovedIt() {
        assertEquals(
            listOf("thoughts", "main"),
            reconcilePhoneTabs(
                PhoneTabOrder.Explicit(listOf("thoughts", "main")),
                listOf(main, thoughts),
            ),
        )
    }

    // --- moving -------------------------------------------------------------------------------

    @Test
    fun movingForwardPlacesAfterTheAnchor() {
        assertEquals(
            listOf("thoughts", "main", "death"),
            moveInSavedOrder(listOf("main", "thoughts", "death"), moved = "main", anchor = "thoughts", forward = true),
        )
    }

    @Test
    fun movingBackwardPlacesBeforeTheAnchor() {
        assertEquals(
            listOf("death", "main", "thoughts"),
            moveInSavedOrder(listOf("main", "thoughts", "death"), moved = "death", anchor = "main", forward = false),
        )
    }

    @Test
    fun movingSkipsOverAWindowThatIsNotOnScreen() {
        // "away" is saved but unannounced, so the strip shows main, thoughts. Dragging thoughts
        // before main must not disturb where "away" sits in the saved order.
        assertEquals(
            listOf("thoughts", "main", "away"),
            moveInSavedOrder(
                listOf("main", "away", "thoughts"),
                moved = "thoughts",
                anchor = "main",
                forward = false,
            ),
        )
    }

    @Test
    fun movingAnUnknownNameChangesNothing() {
        val saved = listOf("main", "thoughts")
        assertEquals(saved, moveInSavedOrder(saved, moved = "ghost", anchor = "main", forward = true))
        assertEquals(saved, moveInSavedOrder(saved, moved = "main", anchor = "ghost", forward = true))
    }
}
