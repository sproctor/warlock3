package warlockfe.warlock3.compose.ui.game

import androidx.compose.runtime.mutableStateOf
import com.seanproctor.docking.layout.dockLayout
import com.seanproctor.docking.model.DockRegion
import com.seanproctor.docking.model.DockableId
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
        location: WindowLocation?,
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
                        location = location.takeUnless { it == WindowLocation.MAIN },
                    ),
                ),
            style = warlockfe.warlock3.compose.util.SAFE_DEFAULT_STYLE,
            data = null,
        )

    private fun openWindows(vararg windows: Pair<String, WindowLocation>): Map<String, OpenGameWindow> =
        windows.associate { (name, location) -> name to OpenGameWindow(uiState(name, location)) }

    private fun newState(): DockState =
        DockState(
            initialLayout =
                dockLayout {
                    mainWindow {
                        dock(MAIN_WINDOW_NAME)
                    }
                },
        )

    private fun register(state: DockState): (String) -> Unit =
        { name ->
            val id = DockableId(name)
            if (!state.registry.isRegistered(id)) {
                state.registry.register(DockableSpec(id = id, title = { name }, content = {}))
            }
        }

    @Test
    fun reconcileDocksEveryOpenWindowAndKeepsMain() {
        val state = newState()
        val windows =
            openWindows(
                MAIN_WINDOW_NAME to WindowLocation.MAIN,
                "thoughts" to WindowLocation.LEFT,
                "logons" to WindowLocation.LEFT,
                "room" to WindowLocation.TOP,
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
                MAIN_WINDOW_NAME to WindowLocation.MAIN,
                "thoughts" to WindowLocation.LEFT,
            )
        reconcile(state, before, register(state))
        val after = openWindows(MAIN_WINDOW_NAME to WindowLocation.MAIN)
        reconcile(state, after, register(state))
        assertFalse(state.isOpen(DockableId("thoughts")))
        assertTrue(state.isOpen(DockableId(MAIN_WINDOW_NAME)))
    }

    @Test
    fun reconcileLeavesAnExistingArrangementAlone() {
        val state = newState()
        val windows =
            openWindows(
                MAIN_WINDOW_NAME to WindowLocation.MAIN,
                "thoughts" to WindowLocation.LEFT,
            )
        reconcile(state, windows, register(state))
        val arranged = state.layout
        reconcile(state, windows, register(state))
        assertEquals(arranged, state.layout)
    }

    @Test
    fun firstWindowOfADockSplitsFreshAndLaterOnesStackOntoIt() {
        val state = newState()
        val windows =
            openWindows(
                MAIN_WINDOW_NAME to WindowLocation.MAIN,
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
        assertEquals(DockTarget.Root(), first.target)
        assertEquals(DockRegion.West, first.region)
        register(state)("thoughts")
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
    fun topAndBottomBandsSplitOffTheMainWindow() {
        val state = newState()
        val windows =
            openWindows(
                MAIN_WINDOW_NAME to WindowLocation.MAIN,
                "room" to WindowLocation.TOP,
            )
        val placement =
            defaultPlacement(
                name = "room",
                location = WindowLocation.TOP,
                layout = state.layout,
                openWindows = windows,
            )
        assertEquals(DockTarget.OnDockable(DockableId(MAIN_WINDOW_NAME)), placement.target)
        assertEquals(DockRegion.North, placement.region)
    }

    @Test
    fun movedWindowStopsAttractingItsOldDockMates() {
        val state = newState()
        val registerWindow = register(state)
        val mainId = DockableId(MAIN_WINDOW_NAME)
        reconcile(
            state,
            openWindows(
                MAIN_WINDOW_NAME to WindowLocation.MAIN,
                "room" to WindowLocation.TOP,
            ),
            registerWindow,
        )
        val roomId = DockableId("room")
        assertEquals(DockRegion.North, relativeRegion(state.layout.mainWindow.root!!, roomId, mainId))

        // The user drags the top-seeded window below main.
        state.dock(roomId, DockTarget.OnDockable(mainId), DockRegion.South)
        assertEquals(DockRegion.South, relativeRegion(state.layout.mainWindow.root!!, roomId, mainId))

        // The next window remembered as TOP must open a fresh band north of main, not chase the
        // moved window to the bottom.
        reconcile(
            state,
            openWindows(
                MAIN_WINDOW_NAME to WindowLocation.MAIN,
                "room" to WindowLocation.TOP,
                "atmospherics" to WindowLocation.TOP,
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
        // A top band exists; the right column must still open outside it, not inside the center
        // column.
        reconcile(
            state,
            openWindows(
                MAIN_WINDOW_NAME to WindowLocation.MAIN,
                "room" to WindowLocation.TOP,
            ),
            registerWindow,
        )
        val windows =
            openWindows(
                MAIN_WINDOW_NAME to WindowLocation.MAIN,
                "room" to WindowLocation.TOP,
                "inv" to WindowLocation.RIGHT,
            )
        val placement =
            defaultPlacement(
                name = "inv",
                location = WindowLocation.RIGHT,
                layout = state.layout,
                openWindows = windows,
            )
        assertEquals(DockTarget.Root(), placement.target)
        assertEquals(DockRegion.East, placement.region)
        reconcile(state, windows, registerWindow)
        val tree = state.layout.mainWindow.root!!
        // Right of the main window and of the top band alike.
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
                        MAIN_WINDOW_NAME to WindowLocation.MAIN,
                        "room" to WindowLocation.TOP,
                        "inv" to WindowLocation.RIGHT,
                        "combat" to WindowLocation.RIGHT,
                    ),
            )
        assertEquals(DockTarget.OnDockable(DockableId("inv")), second.target)
        assertEquals(DockRegion.South, second.region)
    }

    @Test
    fun windowDraggedIntoARegionBecomesAStackingTarget() {
        val state = newState()
        val registerWindow = register(state)
        val mainId = DockableId(MAIN_WINDOW_NAME)
        reconcile(
            state,
            openWindows(
                MAIN_WINDOW_NAME to WindowLocation.MAIN,
                "inv" to WindowLocation.RIGHT,
            ),
            registerWindow,
        )
        // The user drags the right-seeded window above main; the next TOP window joins it there.
        state.dock(DockableId("inv"), DockTarget.OnDockable(mainId), DockRegion.North)
        val windows =
            openWindows(
                MAIN_WINDOW_NAME to WindowLocation.MAIN,
                "inv" to WindowLocation.RIGHT,
                "room" to WindowLocation.TOP,
            )
        val placement =
            defaultPlacement(
                name = "room",
                location = WindowLocation.TOP,
                layout = state.layout,
                openWindows = windows,
            )
        assertEquals(DockTarget.OnDockable(DockableId("inv")), placement.target)
        assertEquals(DockRegion.East, placement.region)
    }
}
