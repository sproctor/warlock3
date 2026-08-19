package warlockfe.warlock3.compose.ui.game

import androidx.compose.runtime.mutableStateOf
import com.seanproctor.docking.model.AnchorId
import com.seanproctor.docking.model.DockNode
import com.seanproctor.docking.model.DockRegion
import com.seanproctor.docking.model.DockableId
import com.seanproctor.docking.model.DockableOptions
import com.seanproctor.docking.model.WindowBounds
import com.seanproctor.docking.persistence.DockingPersistence
import com.seanproctor.docking.persistence.captureLayout
import com.seanproctor.docking.persistence.restoreLayout
import com.seanproctor.docking.state.DockState
import com.seanproctor.docking.state.DockTarget
import com.seanproctor.docking.state.DockableSpec
import com.seanproctor.docking.ui.platformDockCapabilities
import warlockfe.warlock3.compose.ui.window.WindowUiState
import warlockfe.warlock3.core.window.WindowInfo
import warlockfe.warlock3.core.window.WindowLocation
import warlockfe.warlock3.core.window.WindowType
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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
                        options =
                            DockableOptions(
                                closable = name != MAIN_WINDOW_NAME,
                                // Mirrors production: detaching is gated on this, so a registrar
                                // that left it at the default would let main float and would test
                                // a rule the app does not have.
                                floatable = name != MAIN_WINDOW_NAME && platformDockCapabilities.floatingWindows,
                                anchor = anchor,
                            ),
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
        reconcile(state, windows, register(state), DockSpotMemory())
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
        reconcile(state, before, register(state), DockSpotMemory())
        val after = openWindows(MAIN_WINDOW_NAME to WindowLocation.CENTER)
        reconcile(state, after, register(state), DockSpotMemory())
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
        reconcile(state, windows, register(state), DockSpotMemory())
        val arranged = state.layout
        reconcile(state, windows, register(state), DockSpotMemory())
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
        assertEquals(DockRegion.South, second.region)
        register(state)("logons", WindowLocation.LEFT.anchor)
        state.dock(DockableId("logons"), second.target, second.region, second.proportion)
        assertEquals(
            DockRegion.South,
            relativeRegion(state.layout.mainWindow.root!!, DockableId("logons"), DockableId("thoughts")),
            "the second window of an area stacks below the first",
        )
    }

    // Each window's share of the layout, multiplied down the split proportions.
    private fun shares(
        node: DockNode,
        weight: Float = 1f,
    ): Map<DockableId, Float> =
        when (node) {
            is DockNode.Leaf -> {
                mapOf(node.dockableId to weight)
            }

            is DockNode.Tabs -> {
                node.tabs.associate { it.dockableId to weight }
            }

            is DockNode.Anchor -> {
                emptyMap()
            }

            is DockNode.Split -> {
                shares(node.first, weight * node.proportion) +
                    shares(node.second, weight * (1f - node.proportion))
            }
        }

    // The game opens a character's windows one after another on connect. Each one takes a share of
    // the area rather than half of whichever window opened last, so what the user ends up looking at
    // is an evenly divided column - not one window at half the height, the next at a quarter, the
    // next at an eighth.
    @Test
    fun windowsOpeningIntoAnAreaEndUpTheSameSize() {
        val state = newState()
        val registerWindow = register(state)
        val column = listOf("thoughts", "logons", "deaths", "arrivals")
        val opened = mutableListOf(MAIN_WINDOW_NAME to WindowLocation.CENTER)
        column.forEach { name ->
            opened += name to WindowLocation.LEFT
            reconcile(state, openWindows(*opened.toTypedArray()), registerWindow, DockSpotMemory())
        }

        val shares = shares(state.layout.mainWindow.root!!)
        val sizes = column.map { shares.getValue(DockableId(it)) }
        sizes.forEach { size ->
            assertTrue(
                abs(size - sizes.first()) < 0.001f,
                "every window in the column should be the same size, got ${column.zip(sizes)}",
            )
        }
    }

    // The rule the sizes come from: a window joining an area takes one Nth of it, N counting the
    // area's windows once it has joined.
    @Test
    fun aWindowJoiningAnAreaTakesOneNthOfIt() {
        val state = newState()
        val registerWindow = register(state)
        val opened = mutableListOf(MAIN_WINDOW_NAME to WindowLocation.CENTER)
        listOf("thoughts", "logons", "deaths").forEachIndexed { index, name ->
            val joining = openWindows(*(opened + (name to WindowLocation.LEFT)).toTypedArray())
            val placement =
                defaultPlacement(
                    name = name,
                    location = WindowLocation.LEFT,
                    layout = state.layout,
                    openWindows = joining,
                )
            // The first fills the area's empty slot outright; the rest take a share of it.
            if (index > 0) {
                assertEquals(1f / (index + 1), placement.proportion, "window ${index + 1} of the column")
            }
            opened += name to WindowLocation.LEFT
            reconcile(state, openWindows(*opened.toTypedArray()), registerWindow, DockSpotMemory())
        }
    }

    @Test
    fun anAreaSurvivesClosingItsLastWindow() {
        val state = newState()
        val registerWindow = register(state)
        val mainId = DockableId(MAIN_WINDOW_NAME)
        val left = WindowLocation.LEFT.anchor
        reconcile(
            state,
            openWindows(MAIN_WINDOW_NAME to WindowLocation.CENTER, "thoughts" to WindowLocation.LEFT),
            registerWindow,
            DockSpotMemory(),
        )
        assertEquals(DockRegion.West, relativeRegion(state.layout.mainWindow.root!!, DockableId("thoughts"), mainId))

        // The user closes the only window in the left column. The column is what the anchor buys us:
        // without it the split would collapse and the area would be gone.
        reconcile(state, openWindows(MAIN_WINDOW_NAME to WindowLocation.CENTER), registerWindow, DockSpotMemory())
        assertTrue(
            state.layout.mainWindow.root!!
                .holdsAnchor(left),
            "the left area should hold its slot",
        )

        // The next left window drops into that slot rather than opening a second column.
        reconcile(
            state,
            openWindows(MAIN_WINDOW_NAME to WindowLocation.CENTER, "logons" to WindowLocation.LEFT),
            registerWindow,
            DockSpotMemory(),
        )
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
        reconcile(state, announced, registerWindow, DockSpotMemory())
        assertEquals(WindowLocation.LEFT.anchor, state.registry[thoughtsId]?.options?.anchor)

        // The user drags it into the right column; the drop, not the announcement, decides the area.
        state.dock(thoughtsId, DockTarget.Root(), DockRegion.East)
        reconcile(state, announced, registerWindow, DockSpotMemory())
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
            DockSpotMemory(),
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
        reconcile(state, windows, register(state), DockSpotMemory())
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
            DockSpotMemory(),
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
            DockSpotMemory(),
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
            DockSpotMemory(),
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
        reconcile(state, windows, registerWindow, DockSpotMemory())
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
        assertEquals(DockRegion.South, second.region)
        registerWindow("combat", WindowLocation.RIGHT.anchor)
        state.dock(DockableId("combat"), second.target, second.region, second.proportion)
        assertEquals(
            DockRegion.South,
            relativeRegion(state.layout.mainWindow.root!!, DockableId("combat"), DockableId("inv")),
            "the second right-column window stacks below the first",
        )
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
            DockSpotMemory(),
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
        reconcile(state, windows, registerWindow, DockSpotMemory())
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
        reconcile(state, announced, registerWindow, DockSpotMemory())
        state.dock(DockableId("room"), DockTarget.OnDockable(DockableId("thoughts")), DockRegion.South)
        reconcile(state, announced, registerWindow, DockSpotMemory())
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
        applyRestoredLayout(state, savedArrangement(announced), announced, register(state), DockSpotMemory())
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
        applyRestoredLayout(state, savedArrangement(announced), onlyMain, register(state), DockSpotMemory())
        // The windows arrive a moment later and fall back to their game-announced spots.
        reconcile(state, announced, register(state), DockSpotMemory())
        assertEquals(
            WindowLocation.CENTER.anchor,
            anchorOfRoom(state),
            "without the gate, room falls back to the location the game announced",
        )
    }

    @Test
    fun anUnreadableSavedLayoutFallsBackToTheDefaults() {
        val state = newState()
        applyRestoredLayout(state, "{not json", announced, register(state), DockSpotMemory())
        // The areas are still there and every window is docked, rather than the connect failing.
        announced.keys.forEach { name ->
            assertTrue(state.isOpen(DockableId(name)), "$name should be docked")
        }
        assertEquals(WindowLocation.CENTER.anchor, anchorOfRoom(state))
    }

    @Test
    fun aCharacterWithNoSavedLayoutGetsTheAnnouncedSpots() {
        val state = newState()
        applyRestoredLayout(state, null, announced, register(state), DockSpotMemory())
        assertEquals(WindowLocation.LEFT.anchor, currentAnchorOf(state.layout.mainWindow.root!!, "thoughts"))
        assertEquals(WindowLocation.CENTER.anchor, anchorOfRoom(state))
    }

    // ----- Detached windows -----

    // Every detaching test below is desktop-only behaviour. On a platform with no OS windows to
    // detach into the capability is off, the menu item is never offered, and these would be
    // asserting that nothing happens - which the merge test at the bottom covers instead.
    private fun assumeFloatingWindows(): Boolean = platformDockCapabilities.floatingWindows

    // The game asks for a window of its own with location='detach', and the real client gives it
    // one. Before windows could be detached at all this was docked into the band with everything
    // else; now the announcement is honoured.
    @Test
    fun aWindowAnnouncedDetachedOpensInItsOwnWindow() {
        if (!assumeFloatingWindows()) return
        val state = newState()
        reconcile(
            state,
            openWindows(
                MAIN_WINDOW_NAME to WindowLocation.CENTER,
                "thoughts" to WindowLocation.LEFT,
                "charsheet" to WindowLocation.DETACHED,
            ),
            register(state),
            DockSpotMemory(),
        )

        assertTrue(isDetached(state, "charsheet"), "a detached announcement should open detached")
        assertFalse(
            state.layout.mainWindow.root!!
                .containsDockable(DockableId("charsheet")),
            "and should not also be sitting in the main window",
        )
        assertFalse(isDetached(state, "thoughts"), "the windows announced for an area still dock")
    }

    // Only the default. Once the character has an arrangement, reconcile places nothing that is
    // already in the layout, so a detached window they docked stays docked across a reconnect.
    @Test
    fun aDetachedWindowTheUserHasDockedStaysDocked() {
        if (!assumeFloatingWindows()) return
        val state = newState()
        val registerWindow = register(state)
        val windows =
            openWindows(
                MAIN_WINDOW_NAME to WindowLocation.CENTER,
                "charsheet" to WindowLocation.DETACHED,
            )
        reconcile(state, windows, registerWindow, DockSpotMemory())
        // The user reattaches it, then the layout is saved and restored on the next connect.
        redockIntoMainWindow(state, windows.getValue("charsheet"), windows)
        val saved = DockingPersistence.encode(state.captureLayout())

        val next = newState()
        applyRestoredLayout(next, saved, windows, register(next), DockSpotMemory())
        assertFalse(isDetached(next, "charsheet"), "the arrangement wins over the announcement")
        assertTrue(next.isOpen(DockableId("charsheet")))
    }

    @Test
    fun theMainWindowCannotBeDetached() {
        val state = newState()
        reconcile(state, announced, register(state), DockSpotMemory())
        assertFalse(
            state.canFloat(DockableId(MAIN_WINDOW_NAME)),
            "main is the fixed point the areas are measured against, so it must stay put",
        )
    }

    @Test
    fun detachingMovesAWindowOutOfTheMainWindow() {
        if (!assumeFloatingWindows()) return
        val state = newState()
        reconcile(state, announced, register(state), DockSpotMemory())
        detachWindow(state, "room")

        assertTrue(isDetached(state, "room"), "room should be in a window of its own")
        assertTrue(state.isOpen(DockableId("room")), "detaching must not close the window")
        assertEquals(1, state.layout.floatingWindows.size)
        assertFalse(
            state.layout.mainWindow.root!!
                .containsDockable(DockableId("room")),
            "a detached window is no longer in the main window's tree",
        )
        assertFalse(isDetached(state, "thoughts"), "the windows left behind stay docked")
    }

    // The area a detached window came from is held open by a placeholder, exactly as when the user
    // closes the last window in it - so the column does not collapse and the window has somewhere
    // to land when it comes back.
    @Test
    fun detachingTheLastWindowOfAnAreaLeavesItsSlotOpen() {
        if (!assumeFloatingWindows()) return
        val state = newState()
        reconcile(state, announced, register(state), DockSpotMemory())
        detachWindow(state, "thoughts")
        assertTrue(
            state.layout.mainWindow.root!!
                .holdsAnchor(WindowLocation.LEFT.anchor),
            "the left column should still be there for the window to come back to",
        )
    }

    @Test
    fun reattachingPutsTheWindowBackInItsArea() {
        if (!assumeFloatingWindows()) return
        val state = newState()
        reconcile(state, announced, register(state), DockSpotMemory())
        detachWindow(state, "thoughts")
        redockIntoMainWindow(state, announced.getValue("thoughts"), announced)

        assertFalse(isDetached(state, "thoughts"))
        assertTrue(state.layout.floatingWindows.isEmpty(), "the emptied window should be gone")
        assertEquals(
            WindowLocation.LEFT.anchor,
            currentAnchorOf(state.layout.mainWindow.root!!, "thoughts"),
            "thoughts should come back to the left column it was announced for",
        )
    }

    // reconcile docks anything the view model has open that the layout does not, and a detached
    // window is docked - just not in the main window. Getting this wrong would drag every detached
    // window home on the next open, close, or drop.
    @Test
    fun reconcileLeavesADetachedWindowWhereItIs() {
        if (!assumeFloatingWindows()) return
        val state = newState()
        val registerWindow = register(state)
        reconcile(state, announced, registerWindow, DockSpotMemory())
        detachWindow(state, "room")
        val detached = state.layout

        reconcile(state, announced, registerWindow, DockSpotMemory())
        assertEquals(detached, state.layout, "a reconcile pass should not reel a detached window back in")
    }

    // A window the game closes while it is detached is undocked like any other, and takes its empty
    // window with it rather than leaving an empty frame on screen.
    @Test
    fun closingADetachedWindowInTheGameTakesTheWindowWithIt() {
        if (!assumeFloatingWindows()) return
        val state = newState()
        val registerWindow = register(state)
        reconcile(state, announced, registerWindow, DockSpotMemory())
        detachWindow(state, "room")

        reconcile(
            state,
            openWindows(
                MAIN_WINDOW_NAME to WindowLocation.CENTER,
                "thoughts" to WindowLocation.LEFT,
            ),
            registerWindow,
            DockSpotMemory(),
        )
        assertFalse(state.isOpen(DockableId("room")))
        assertTrue(state.layout.floatingWindows.isEmpty())
    }

    @Test
    fun aDetachedArrangementSurvivesASaveAndRestore() {
        if (!assumeFloatingWindows()) return
        val saving = newState()
        reconcile(saving, announced, register(saving), DockSpotMemory())
        detachWindow(saving, "room")
        val saved = DockingPersistence.encode(saving.captureLayout())

        val state = newState()
        applyRestoredLayout(state, saved, announced, register(state), DockSpotMemory())
        assertTrue(isDetached(state, "room"), "room should come back detached")
        assertTrue(state.isOpen(DockableId("thoughts")), "the docked windows come back too")
    }

    // The same saved layout opened where there are no OS windows to put a detached window in: its
    // contents are grafted into the main window rather than dropped, so a character who detaches on
    // the desktop does not lose the window on their phone.
    @Test
    fun aDetachedArrangementMergesWhereThereAreNoFloatingWindows() {
        if (!assumeFloatingWindows()) return
        val saving = newState()
        reconcile(saving, announced, register(saving), DockSpotMemory())
        detachWindow(saving, "room")
        val persisted = DockingPersistence.decode(DockingPersistence.encode(saving.captureLayout()))

        val state = newState()
        state.restoreLayout(persisted, mergeFloatingWindows = true)
        assertTrue(state.layout.floatingWindows.isEmpty())
        assertTrue(
            state.layout.mainWindow.root!!
                .containsDockable(DockableId("room")),
            "room should be folded back into the main window rather than lost",
        )
    }

    // --- Remembering where a closed window was ---

    // Top to bottom down the column, which is what "where it was" means for a stacked area.
    private fun orderedDockables(node: DockNode): List<String> =
        when (node) {
            is DockNode.Leaf -> listOf(node.dockableId.value)
            is DockNode.Tabs -> node.tabs.map { it.dockableId.value }
            is DockNode.Anchor -> emptyList()
            is DockNode.Split -> orderedDockables(node.first) + orderedDockables(node.second)
        }

    private fun order(state: DockState): List<String> = orderedDockables(state.layout.mainWindow.root!!)

    private fun column() =
        openWindows(
            MAIN_WINDOW_NAME to WindowLocation.CENTER,
            "thoughts" to WindowLocation.LEFT,
            "logons" to WindowLocation.LEFT,
            "deaths" to WindowLocation.LEFT,
        )

    // The point of the whole thing: close a window out of the middle of an arranged column, reopen
    // it, and it is back between the same two windows at the same size.
    @Test
    fun aReopenedWindowGoesBackWhereItWas() {
        val state = newState()
        val registerWindow = register(state)
        val spots = DockSpotMemory()
        reconcile(state, column(), registerWindow, spots)
        val orderBefore = order(state)
        val sizesBefore = shares(state.layout.mainWindow.root!!)

        reconcile(state, column().filterKeys { it != "logons" }, registerWindow, spots)
        reconcile(state, column(), registerWindow, spots)

        assertEquals(orderBefore, order(state))
        val after = shares(state.layout.mainWindow.root!!)
        listOf("thoughts", "logons", "deaths").forEach { name ->
            val id = DockableId(name)
            assertEquals(sizesBefore[id]!!, after[id]!!, absoluteTolerance = 0.02f, message = "$name resized")
        }
    }

    // Pins that the test above is testing something: with no memory carried across the calls, the
    // reopened window is placed by its announced area and comes back at the end of the column.
    @Test
    fun withoutTheMemoryAReopenedWindowIsPlacedByItsAnnouncedArea() {
        val state = newState()
        val registerWindow = register(state)
        reconcile(state, column(), registerWindow, DockSpotMemory())
        val orderBefore = order(state)

        reconcile(state, column().filterKeys { it != "thoughts" }, registerWindow, DockSpotMemory())
        reconcile(state, column(), registerWindow, DockSpotMemory())

        assertNotEquals(orderBefore, order(state))
    }

    // Everything it sat beside has closed too, so there is nothing left to be next to. It should
    // fall back to its announced area rather than throwing or landing somewhere arbitrary.
    @Test
    fun aWindowWhoseNeighboursAllClosedFallsBackToItsAnnouncedArea() {
        val state = newState()
        val registerWindow = register(state)
        val spots = DockSpotMemory()
        val announced =
            openWindows(
                MAIN_WINDOW_NAME to WindowLocation.CENTER,
                "thoughts" to WindowLocation.LEFT,
                "logons" to WindowLocation.LEFT,
            )
        reconcile(state, announced, registerWindow, spots)

        reconcile(state, openWindows(MAIN_WINDOW_NAME to WindowLocation.CENTER), registerWindow, spots)
        reconcile(state, announced, registerWindow, spots)

        // The announced-area arrangement, not merely the same three names in some order.
        val fresh = newState()
        reconcile(fresh, announced, register(fresh), DockSpotMemory())
        assertEquals(order(fresh), order(state))
    }

    // A spot is spent by the reopen that uses it, so a window moved afterwards is not yanked back to
    // a stale one the next time it is closed and opened.
    @Test
    fun aSpotIsNotReusedAfterItHasBeenSpent() {
        val spots = DockSpotMemory()
        spots.remember("thoughts", RememberedSpot(listOf("logons"), DockRegion.South, 0.5f))

        assertNotNull(spots.spend("thoughts"))
        assertNull(spots.spend("thoughts"))
    }

    @Test
    fun aWindowThatIsNotDockedHasNoSpotToRemember() {
        assertNull(rememberedSpotOf(newState(), "never-docked"))
    }

    // --- Spots surviving a restart ---

    // The point of persisting: close a window, quit, come back, reopen it, and it is still where it
    // was. The second session is a fresh DockState and a fresh memory, restored from what was saved.
    @Test
    fun aSpotSurvivesIntoTheNextSession() {
        val first = newState()
        val firstSpots = DockSpotMemory()
        reconcile(first, column(), register(first), firstSpots)
        val orderBefore = order(first)
        reconcile(first, column().filterKeys { it != "logons" }, register(first), firstSpots)
        val savedLayout = DockingPersistence.encode(first.captureLayout())
        val savedSpots = firstSpots.encode()

        val next = newState()
        val nextSpots = DockSpotMemory()
        nextSpots.restore(savedSpots)
        applyRestoredLayout(next, savedLayout, column().filterKeys { it != "logons" }, register(next), nextSpots)
        reconcile(next, column(), register(next), nextSpots)

        assertEquals(orderBefore, order(next))
    }

    @Test
    fun spotsSurviveTheirOwnRoundTrip() {
        val spots = DockSpotMemory()
        spots.remember("thoughts", RememberedSpot(listOf("logons", "deaths"), DockRegion.South, 0.25f))
        spots.remember(
            "room",
            RememberedSpot(emptyList(), DockRegion.Center, 0.5f, detached = true, bounds = WindowBounds(1f, 2f, 3f, 4f)),
        )

        val restored = DockSpotMemory().apply { restore(spots.encode()) }

        assertEquals(RememberedSpot(listOf("logons", "deaths"), DockRegion.South, 0.25f), restored.spend("thoughts"))
        assertEquals(
            RememberedSpot(emptyList(), DockRegion.Center, 0.5f, detached = true, bounds = WindowBounds(1f, 2f, 3f, 4f)),
            restored.spend("room"),
        )
    }

    // A character whose saved spots are unreadable loses the saved ones, and nothing else: whatever
    // this session has already recorded stays.
    @Test
    fun unreadableSpotsAreDiscardedWithoutDisturbingThisSession() {
        val spots = DockSpotMemory()
        spots.remember("thoughts", RememberedSpot(listOf("logons"), DockRegion.South, 0.5f))

        spots.restore("{not json")

        assertEquals(RememberedSpot(listOf("logons"), DockRegion.South, 0.5f), spots.spend("thoughts"))
    }

    // A window closed while the character was still being identified: the restore that follows must
    // not wipe the spot just recorded for it.
    @Test
    fun aSpotRecordedBeforeTheRestoreSurvivesIt() {
        val spots = DockSpotMemory()
        spots.remember("thoughts", RememberedSpot(listOf("deaths"), DockRegion.North, 0.4f))

        spots.restore("""{"thoughts":{"siblings":["logons"],"region":"South","proportion":0.9}}""")

        assertEquals(RememberedSpot(listOf("deaths"), DockRegion.North, 0.4f), spots.spend("thoughts"))
    }

    // A field written by a later build must cost at most its own spot, and preferably nothing.
    @Test
    fun anUnknownFieldDoesNotDiscardTheSpot() {
        val spots = DockSpotMemory()

        spots.restore("""{"logons":{"siblings":["deaths"],"region":"South","proportion":0.25,"futureField":7}}""")

        assertEquals(RememberedSpot(listOf("deaths"), DockRegion.South, 0.25f), spots.spend("logons"))
    }

    // One unreadable entry must not take the readable ones with it.
    @Test
    fun oneUnreadableEntryDoesNotTakeTheOthers() {
        val spots = DockSpotMemory()

        spots.restore(
            """{"thoughts":{"region":12345,"proportion":"nonsense"},""" +
                """"logons":{"siblings":["deaths"],"region":"South","proportion":0.25}}""",
        )

        assertNull(spots.spend("thoughts"))
        assertEquals(RememberedSpot(listOf("deaths"), DockRegion.South, 0.25f), spots.spend("logons"))
    }

    // "Nothing worth recording now" is not "forget what we knew".
    @Test
    fun rememberingNothingKeepsWhatWasAlreadyKnown() {
        val spots = DockSpotMemory()
        spots.remember("thoughts", RememberedSpot(listOf("logons"), DockRegion.South, 0.5f))

        spots.remember("thoughts", null)

        assertEquals(RememberedSpot(listOf("logons"), DockRegion.South, 0.5f), spots.spend("thoughts"))
    }

    // Windows the game has stopped sending would otherwise accumulate a spot each, forever, and be
    // re-serialized into the character's settings on every save.
    @Test
    fun pruningDropsSpotsForWindowsThatAreGone() {
        val spots = DockSpotMemory()
        spots.remember("thoughts", RememberedSpot(listOf("logons"), DockRegion.South, 0.5f))
        spots.remember("ancient", RememberedSpot(listOf("logons"), DockRegion.South, 0.5f))

        spots.prune(setOf("thoughts", "logons"))

        assertNotNull(spots.spend("thoughts"))
        assertNull(spots.spend("ancient"))
    }

    // Only a real change is worth a database write; drags and resizes move the layout but no spot.
    @Test
    fun encodingIsOnlyAskedForWhenSomethingMoved() {
        val spots = DockSpotMemory()
        spots.remember("thoughts", RememberedSpot(listOf("logons"), DockRegion.South, 0.5f))
        assertTrue(spots.isDirty())

        spots.encode()

        assertFalse(spots.isDirty())
        spots.remember("thoughts", RememberedSpot(listOf("logons"), DockRegion.South, 0.5f))
        assertFalse(spots.isDirty(), "re-recording an identical spot is not a change")
    }

    @Test
    fun noSavedSpotsIsNotAnError() {
        val spots = DockSpotMemory()
        spots.restore(null)
        spots.restore("")
        assertNull(spots.spend("thoughts"))
    }

    // A region name this build does not know (an older client reading a newer save) drops that one
    // spot rather than the whole set.
    @Test
    fun anUnknownRegionDropsOnlyItsOwnSpot() {
        val spots = DockSpotMemory()

        spots.restore(
            """{"thoughts":{"region":"Sideways","proportion":0.5},""" +
                """"logons":{"siblings":["deaths"],"region":"South","proportion":0.25}}""",
        )

        assertNull(spots.spend("thoughts"))
        assertEquals(RememberedSpot(listOf("deaths"), DockRegion.South, 0.25f), spots.spend("logons"))
    }

    // Closing two windows at once and reopening them both. Reported by review: the capture loop ran
    // against a tree earlier iterations had already changed, and a spot was spent even when it could
    // not be used, so both windows lost their places for good.
    @Test
    fun twoWindowsClosedTogetherBothComeBack() {
        val state = newState()
        val registerWindow = register(state)
        val spots = DockSpotMemory()
        reconcile(state, column(), registerWindow, spots)
        val orderBefore = order(state)

        reconcile(state, column().filterKeys { it != "logons" && it != "deaths" }, registerWindow, spots)
        reconcile(state, column(), registerWindow, spots)

        assertEquals(orderBefore, order(state))
    }

    // --- The three the first review round left open ---

    // A window that was the first of a strip came back last, because docking Center appends.
    @Test
    fun aTabbedWindowRejoinsTheStripAtItsOwnIndex() {
        val state = newState()
        val registerWindow = register(state)
        val spots = DockSpotMemory()
        val windows =
            openWindows(
                MAIN_WINDOW_NAME to WindowLocation.CENTER,
                "thoughts" to WindowLocation.LEFT,
                "logons" to WindowLocation.LEFT,
                "deaths" to WindowLocation.LEFT,
            )
        reconcile(state, windows, registerWindow, spots)
        // Gather the three into one strip, so "thoughts" is the first of three tabs.
        state.dock(DockableId("logons"), DockTarget.OnDockable(DockableId("thoughts")), DockRegion.Center)
        state.dock(DockableId("deaths"), DockTarget.OnDockable(DockableId("thoughts")), DockRegion.Center)
        val stripBefore = tabOrder(state)
        assertEquals("thoughts", stripBefore.first())

        reconcile(state, windows.filterKeys { it != "thoughts" }, registerWindow, spots)
        reconcile(state, windows, registerWindow, spots)

        assertEquals(stripBefore, tabOrder(state))
    }

    private fun tabOrder(state: DockState): List<String> {
        fun find(node: DockNode): DockNode.Tabs? =
            when (node) {
                is DockNode.Tabs -> node
                is DockNode.Split -> find(node.first) ?: find(node.second)
                else -> null
            }
        return find(state.layout.mainWindow.root!!)?.tabs?.map { it.dockableId.value } ?: emptyList()
    }

    // A window whose sibling was a subtree of two, one of which has since closed. Review round one
    // read this as a bug - the old proportion applied to a smaller thing - but the space the closed
    // window had was already handed to the ones remaining, so the survivors fill the same extent the
    // group did and the old share of them is the size it had. Scaling to the survivors was tried, and
    // made the window grow instead. This pins the behaviour so it is not "fixed" again.
    @Test
    fun aWindowComesBackTheRightSizeWhenOnlySomeNeighboursSurvive() {
        val state = newState()
        val registerWindow = register(state)
        val spots = DockSpotMemory()
        reconcile(state, column(), registerWindow, spots)
        // deaths sits opposite the subtree holding thoughts and logons.
        val spot = rememberedSpotOf(state, "deaths")!!
        assertEquals(setOf("thoughts", "logons"), spot.siblings.toSet())

        reconcile(state, column().filterKeys { it != "deaths" }, registerWindow, spots)
        reconcile(state, column().filterKeys { it != "deaths" && it != "logons" }, registerWindow, spots)
        reconcile(state, column().filterKeys { it != "logons" }, registerWindow, spots)

        // Against the one surviving sibling, its share of that pair should still be the share it had
        // of the pair it left - a half of what it is now beside, not a quarter.
        val after = shares(state.layout.mainWindow.root!!)
        val deaths = after[DockableId("deaths")]!!
        val thoughts = after[DockableId("thoughts")]!!
        assertEquals(
            spot.proportion,
            deaths / (deaths + thoughts),
            absoluteTolerance = 0.02f,
            message = "deaths came back at ${deaths / (deaths + thoughts)} of its pair, having had ${spot.proportion}",
        )
    }

    @Test
    fun tabIndexAndSharesSurviveTheRoundTrip() {
        val spots = DockSpotMemory()
        val spot =
            RememberedSpot(
                siblings = listOf("logons", "deaths"),
                region = DockRegion.South,
                proportion = 0.25f,
                tabIndex = 2,
            )
        spots.remember("thoughts", spot)

        val restored = DockSpotMemory().apply { restore(spots.encode()) }

        assertEquals(spot, restored.spend("thoughts"))
    }
}
