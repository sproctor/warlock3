package warlockfe.warlock3.compose.ui.game

import androidx.compose.runtime.mutableStateOf
import com.seanproctor.docking.model.AnchorId
import com.seanproctor.docking.model.DockRegion
import com.seanproctor.docking.model.DockableId
import com.seanproctor.docking.model.DockableOptions
import com.seanproctor.docking.persistence.DockingPersistence
import com.seanproctor.docking.persistence.captureLayout
import com.seanproctor.docking.persistence.restoreLayout
import com.seanproctor.docking.state.DockState
import com.seanproctor.docking.state.DockTarget
import com.seanproctor.docking.state.DockableSpec
import warlockfe.warlock3.compose.ui.window.WindowUiState
import warlockfe.warlock3.core.window.WindowInfo
import warlockfe.warlock3.core.window.WindowLocation
import warlockfe.warlock3.core.window.WindowType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GameDockingTest {
    private fun uiState(
        name: String,
        location: WindowLocation,
    ): WindowUiState =
        WindowUiState(
            name = name,
            // The game-announced location rides on the window info, exactly as in production.
            windowInfo =
                mutableStateOf(
                    WindowInfo(
                        name = name,
                        title = name,
                        subtitle = null,
                        windowType = WindowType.STREAM,
                        showTimestamps = false,
                        backgroundImage = null,
                        location = location,
                    ),
                ),
            style = warlockfe.warlock3.compose.util.SAFE_DEFAULT_STYLE,
            data = null,
        )

    // Main's location is ignored: it is placed by name, not by anything the game announced.
    private fun openWindows(vararg windows: Pair<String, WindowLocation>): Map<String, OpenGameWindow> =
        windows.associate { (name, location) -> name to OpenGameWindow(uiState(name, location)) }

    // The layout the app ships, areas and all - a bare main window would exercise placement
    // rules production never takes.
    private fun newState(): DockState = DockState(initialLayout = gameDockLayout())

    // The production registrar in miniature: the anchor is what the library reads back through
    // DockableRegistry.anchorOf when it decides whether to restore a placeholder, so a test that
    // does not carry it would never see an area survive close-all.
    private fun register(state: DockState): (String, AnchorId?) -> Unit =
        { name, anchor ->
            val id = DockableId(name)
            if (state.registry[id]?.options?.anchor != anchor || !state.registry.isRegistered(id)) {
                state.registry.register(
                    DockableSpec(
                        id = id,
                        options = DockableOptions(closable = name != MAIN_WINDOW_NAME, anchor = anchor),
                        title = { name },
                        content = {},
                    ),
                )
            }
        }

    @Test
    fun reconcileDocksEveryOpenWindowAndKeepsMain() {
        val state = newState()
        val windows =
            openWindows(
                MAIN_WINDOW_NAME to WindowLocation.CENTER,
                "thoughts" to WindowLocation.LEFT,
                "logons" to WindowLocation.LEFT,
                "room" to WindowLocation.CENTER,
            )
        reconcile(state, windows, register(state))
        windows.keys.forEach { name ->
            assertTrue(state.isOpen(DockableId(name)), "$name should be docked")
        }
    }

    @Test
    fun reconcileUndocksWindowsThatClosed() {
        val state = newState()
        val before =
            openWindows(
                MAIN_WINDOW_NAME to WindowLocation.CENTER,
                "thoughts" to WindowLocation.LEFT,
            )
        reconcile(state, before, register(state))
        val after = openWindows(MAIN_WINDOW_NAME to WindowLocation.CENTER)
        reconcile(state, after, register(state))
        assertFalse(state.isOpen(DockableId("thoughts")))
        assertTrue(state.isOpen(DockableId(MAIN_WINDOW_NAME)))
    }

    @Test
    fun reconcileLeavesAnExistingArrangementAlone() {
        val state = newState()
        val windows =
            openWindows(
                MAIN_WINDOW_NAME to WindowLocation.CENTER,
                "thoughts" to WindowLocation.LEFT,
            )
        reconcile(state, windows, register(state))
        val arranged = state.layout
        reconcile(state, windows, register(state))
        assertEquals(arranged, state.layout)
    }

    @Test
    fun firstWindowOfAnAreaFillsItsAnchorAndLaterOnesStackOntoIt() {
        val state = newState()
        val windows =
            openWindows(
                MAIN_WINDOW_NAME to WindowLocation.CENTER,
                "thoughts" to WindowLocation.LEFT,
                "logons" to WindowLocation.LEFT,
            )
        val first =
            defaultPlacement(
                name = "thoughts",
                location = WindowLocation.LEFT,
                layout = state.layout,
                openWindows = windows,
            )
        // The left area is declared up front, so the first window into it replaces that
        // placeholder rather than splitting a new column off the root.
        assertEquals(DockTarget.Anchor(WindowLocation.LEFT.anchor), first.target)
        register(state)("thoughts", WindowLocation.LEFT.anchor)
        state.dock(DockableId("thoughts"), first.target, first.region)
        val second =
            defaultPlacement(
                name = "logons",
                location = WindowLocation.LEFT,
                layout = state.layout,
                openWindows = windows,
            )
        assertEquals(DockTarget.OnDockable(DockableId("thoughts")), second.target)
        assertEquals(DockRegion.South, second.region)
    }

    @Test
    fun anAreaSurvivesClosingItsLastWindow() {
        val state = newState()
        val registerWindow = register(state)
        val mainId = DockableId(MAIN_WINDOW_NAME)
        val left = WindowLocation.LEFT.anchor
        reconcile(state, openWindows(MAIN_WINDOW_NAME to WindowLocation.CENTER, "thoughts" to WindowLocation.LEFT), registerWindow)
        assertEquals(DockRegion.West, relativeRegion(state.layout.mainWindow.root!!, DockableId("thoughts"), mainId))

        // The user closes the only window in the left column. The column is what the anchor buys us:
        // without it the split would collapse and the area would be gone.
        reconcile(state, openWindows(MAIN_WINDOW_NAME to WindowLocation.CENTER), registerWindow)
        assertTrue(
            state.layout.mainWindow.root!!
                .holdsAnchor(left),
            "the left area should hold its slot",
        )

        // The next left window drops into that slot rather than opening a second column.
        reconcile(state, openWindows(MAIN_WINDOW_NAME to WindowLocation.CENTER, "logons" to WindowLocation.LEFT), registerWindow)
        assertEquals(DockRegion.West, relativeRegion(state.layout.mainWindow.root!!, DockableId("logons"), mainId))
        assertFalse(
            state.layout.mainWindow.root!!
                .holdsAnchor(left),
            "the placeholder should have been replaced",
        )
    }

    @Test
    fun aDraggedWindowTakesTheAreaItLandedIn() {
        val state = newState()
        val registerWindow = register(state)
        val thoughtsId = DockableId("thoughts")
        // The announcement says left, and stays saying left for the whole test.
        val announced = openWindows(MAIN_WINDOW_NAME to WindowLocation.CENTER, "thoughts" to WindowLocation.LEFT)
        reconcile(state, announced, registerWindow)
        assertEquals(WindowLocation.LEFT.anchor, state.registry[thoughtsId]?.options?.anchor)

        // The user drags it into the right column; the drop, not the announcement, decides the area.
        state.dock(thoughtsId, DockTarget.Root(), DockRegion.East)
        reconcile(state, announced, registerWindow)
        assertEquals(WindowLocation.RIGHT.anchor, state.registry[thoughtsId]?.options?.anchor)

        // The left column it came from stays open. A move undocks before it re-docks, so vacating
        // the area restores its placeholder exactly as closing the window would.
        assertTrue(
            state.layout.mainWindow.root!!
                .holdsAnchor(WindowLocation.LEFT.anchor),
            "the left area should hold its slot after its last window was dragged out",
        )

        // Which is what makes the slot useful: the next left window lands back in it, rather than
        // splitting a second column off the root.
        reconcile(
            state,
            openWindows(
                MAIN_WINDOW_NAME to WindowLocation.CENTER,
                "thoughts" to WindowLocation.LEFT,
                "logons" to WindowLocation.LEFT,
            ),
            registerWindow,
        )
        val root = state.layout.mainWindow.root!!
        assertFalse(root.holdsAnchor(WindowLocation.LEFT.anchor), "the placeholder should have been replaced")
        assertEquals(DockRegion.West, relativeRegion(root, DockableId("logons"), DockableId(MAIN_WINDOW_NAME)))
        // ...and the dragged window is still where the user put it.
        assertEquals(DockRegion.East, relativeRegion(root, thoughtsId, DockableId(MAIN_WINDOW_NAME)))
    }

    @Test
    fun aWindowOpenAheadOfItsAnnouncementCenters() {
        // A window can be docked before its info arrives (a restored one, or a stream the game has
        // not described yet). It gets the same fallback an unplaced window does, so it never lands
        // somewhere the announcement then has to correct.
        val pending =
            OpenGameWindow(
                WindowUiState(
                    name = "uberbar",
                    windowInfo = mutableStateOf(null),
                    style = warlockfe.warlock3.compose.util.SAFE_DEFAULT_STYLE,
                    data = null,
                ),
            )
        assertEquals(WindowLocation.CENTER, pending.location)
    }

    @Test
    fun theCenterBandFillsItsAnchorAboveMain() {
        val state = newState()
        val windows =
            openWindows(
                MAIN_WINDOW_NAME to WindowLocation.CENTER,
                "room" to WindowLocation.CENTER,
            )
        val placement =
            defaultPlacement(
                name = "room",
                location = WindowLocation.CENTER,
                layout = state.layout,
                openWindows = windows,
            )
        assertEquals(DockTarget.Anchor(WindowLocation.CENTER.anchor), placement.target)
        // Which is where the band was declared: above the main text window.
        reconcile(state, windows, register(state))
        assertEquals(
            DockRegion.North,
            relativeRegion(state.layout.mainWindow.root!!, DockableId("room"), DockableId(MAIN_WINDOW_NAME)),
        )
    }

    @Test
    fun movedWindowStopsAttractingItsOldDockMates() {
        val state = newState()
        val registerWindow = register(state)
        val mainId = DockableId(MAIN_WINDOW_NAME)
        reconcile(
            state,
            openWindows(
                MAIN_WINDOW_NAME to WindowLocation.CENTER,
                "room" to WindowLocation.CENTER,
            ),
            registerWindow,
        )
        val roomId = DockableId("room")
        assertEquals(DockRegion.North, relativeRegion(state.layout.mainWindow.root!!, roomId, mainId))

        // The user drags the top-seeded window below main.
        state.dock(roomId, DockTarget.OnDockable(mainId), DockRegion.South)
        assertEquals(DockRegion.South, relativeRegion(state.layout.mainWindow.root!!, roomId, mainId))

        // The next center window must open a fresh band north of main, not chase the moved
        // window to the bottom.
        reconcile(
            state,
            openWindows(
                MAIN_WINDOW_NAME to WindowLocation.CENTER,
                "room" to WindowLocation.CENTER,
                "atmospherics" to WindowLocation.CENTER,
            ),
            registerWindow,
        )
        assertEquals(
            DockRegion.North,
            relativeRegion(state.layout.mainWindow.root!!, DockableId("atmospherics"), mainId),
        )
    }

    @Test
    fun rightColumnOpensAtTheRootRightOfEverything() {
        val state = newState()
        val registerWindow = register(state)
        val mainId = DockableId(MAIN_WINDOW_NAME)
        // A center band exists; the right column must still open outside it, not inside the
        // center column.
        reconcile(
            state,
            openWindows(
                MAIN_WINDOW_NAME to WindowLocation.CENTER,
                "room" to WindowLocation.CENTER,
            ),
            registerWindow,
        )
        val windows =
            openWindows(
                MAIN_WINDOW_NAME to WindowLocation.CENTER,
                "room" to WindowLocation.CENTER,
                "inv" to WindowLocation.RIGHT,
            )
        val placement =
            defaultPlacement(
                name = "inv",
                location = WindowLocation.RIGHT,
                layout = state.layout,
                openWindows = windows,
            )
        assertEquals(DockTarget.Anchor(WindowLocation.RIGHT.anchor), placement.target)
        reconcile(state, windows, registerWindow)
        val tree = state.layout.mainWindow.root!!
        // Right of the main window and of the center band alike.
        assertEquals(DockRegion.East, relativeRegion(tree, DockableId("inv"), mainId))
        assertEquals(DockRegion.East, relativeRegion(tree, DockableId("inv"), DockableId("room")))
        // The next right window stacks below it rather than opening a second column.
        val second =
            defaultPlacement(
                name = "combat",
                location = WindowLocation.RIGHT,
                layout = state.layout,
                openWindows =
                    openWindows(
                        MAIN_WINDOW_NAME to WindowLocation.CENTER,
                        "room" to WindowLocation.CENTER,
                        "inv" to WindowLocation.RIGHT,
                        "combat" to WindowLocation.RIGHT,
                    ),
            )
        assertEquals(DockTarget.OnDockable(DockableId("inv")), second.target)
        assertEquals(DockRegion.South, second.region)
    }

    @Test
    fun aDeclaredEmptyAreaWinsOverADraggedInNeighbour() {
        val state = newState()
        val registerWindow = register(state)
        val mainId = DockableId(MAIN_WINDOW_NAME)
        reconcile(
            state,
            openWindows(
                MAIN_WINDOW_NAME to WindowLocation.CENTER,
                "inv" to WindowLocation.RIGHT,
            ),
            registerWindow,
        )
        // The user drags the right-seeded window above main. The centre area is declared and
        // still empty, so the next centre window fills it rather than joining the dragged-in
        // window - the area the user has is the one the layout named, not wherever a window
        // happens to have been dropped.
        state.dock(DockableId("inv"), DockTarget.OnDockable(mainId), DockRegion.North)
        val windows =
            openWindows(
                MAIN_WINDOW_NAME to WindowLocation.CENTER,
                "inv" to WindowLocation.RIGHT,
                "room" to WindowLocation.CENTER,
            )
        val placement =
            defaultPlacement(
                name = "room",
                location = WindowLocation.CENTER,
                layout = state.layout,
                openWindows = windows,
            )
        assertEquals(DockTarget.Anchor(WindowLocation.CENTER.anchor), placement.target)

        // Once the area has something in it, the next one stacks onto that, wherever the user
        // has since moved it.
        reconcile(state, windows, registerWindow)
        val next =
            defaultPlacement(
                name = "atmospherics",
                location = WindowLocation.CENTER,
                layout = state.layout,
                openWindows =
                    openWindows(
                        MAIN_WINDOW_NAME to WindowLocation.CENTER,
                        "inv" to WindowLocation.RIGHT,
                        "room" to WindowLocation.CENTER,
                        "atmospherics" to WindowLocation.CENTER,
                    ),
            )
        assertEquals(DockRegion.East, next.region)
    }

    // The arrangement a session saves: the game's defaults, then the user drags room out of the
    // band above main and down into the left column. Returns the layout JSON.
    private fun savedArrangement(announced: Map<String, OpenGameWindow>): String {
        val state = newState()
        val registerWindow = register(state)
        reconcile(state, announced, registerWindow)
        state.dock(DockableId("room"), DockTarget.OnDockable(DockableId("thoughts")), DockRegion.South)
        reconcile(state, announced, registerWindow)
        assertEquals(
            WindowLocation.LEFT.anchor,
            currentAnchorOf(state.layout.mainWindow.root!!, "room"),
            "precondition: the user's arrangement puts room in the left column",
        )
        return DockingPersistence.encode(state.captureLayout())
    }

    private val announced =
        openWindows(
            MAIN_WINDOW_NAME to WindowLocation.CENTER,
            "thoughts" to WindowLocation.LEFT,
            "room" to WindowLocation.CENTER,
        )

    private fun anchorOfRoom(state: DockState): AnchorId? = currentAnchorOf(state.layout.mainWindow.root!!, "room")

    @Test
    fun aRestoreAppliedAgainstTheRestoredWindowsKeepsTheArrangement() {
        val state = newState()
        applyRestoredLayout(state, savedArrangement(announced), announced, register(state))
        assertEquals(
            WindowLocation.LEFT.anchor,
            anchorOfRoom(state),
            "room should come back in the left column the user dragged it to",
        )
    }

    // Why rememberGameDockState waits on GameViewModel.awaitWindowsRestored() before it applies a
    // restore: reconcile cannot tell a window the user closed from one the view model has not
    // restored yet, so applying the arrangement against a list that is still empty undocks the
    // whole thing - and the debounced save writes the result over it a second later, which is what
    // users saw as the layout never being saved. Pinned so the gate is not removed as redundant.
    @Test
    fun aRestoreAppliedBeforeTheWindowsAreRestoredLosesTheArrangement() {
        val state = newState()
        val onlyMain = openWindows(MAIN_WINDOW_NAME to WindowLocation.CENTER)
        applyRestoredLayout(state, savedArrangement(announced), onlyMain, register(state))
        // The windows arrive a moment later and fall back to their game-announced spots.
        reconcile(state, announced, register(state))
        assertEquals(
            WindowLocation.CENTER.anchor,
            anchorOfRoom(state),
            "without the gate, room falls back to the location the game announced",
        )
    }

    @Test
    fun anUnreadableSavedLayoutFallsBackToTheDefaults() {
        val state = newState()
        applyRestoredLayout(state, "{not json", announced, register(state))
        // The areas are still there and every window is docked, rather than the connect failing.
        announced.keys.forEach { name ->
            assertTrue(state.isOpen(DockableId(name)), "$name should be docked")
        }
        assertEquals(WindowLocation.CENTER.anchor, anchorOfRoom(state))
    }

    @Test
    fun aCharacterWithNoSavedLayoutGetsTheAnnouncedSpots() {
        val state = newState()
        applyRestoredLayout(state, null, announced, register(state))
        assertEquals(WindowLocation.LEFT.anchor, currentAnchorOf(state.layout.mainWindow.root!!, "thoughts"))
        assertEquals(WindowLocation.CENTER.anchor, anchorOfRoom(state))
    }
}
