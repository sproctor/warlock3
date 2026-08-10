package warlockfe.warlock3.compose.ui.game

import androidx.compose.runtime.mutableStateOf
import com.seanproctor.docking.layout.dockLayout
import com.seanproctor.docking.model.DockRegion
import com.seanproctor.docking.model.DockableId
import com.seanproctor.docking.state.DockState
import com.seanproctor.docking.state.DockTarget
import com.seanproctor.docking.state.DockableSpec
import warlockfe.warlock3.compose.ui.window.WindowUiState
import warlockfe.warlock3.core.window.WindowLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GameDockingTest {
    private fun uiState(name: String): WindowUiState =
        WindowUiState(
            name = name,
            windowInfo = mutableStateOf(null),
            style = warlockfe.warlock3.compose.util.SAFE_DEFAULT_STYLE,
            width = null,
            height = null,
            data = null,
        )

    private fun openWindows(vararg windows: Pair<String, WindowLocation>): Map<String, OpenGameWindow> =
        windows.associate { (name, location) -> name to OpenGameWindow(location, uiState(name)) }

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
    fun firstWindowOfADockSplitsOffMainAndLaterOnesStackOntoIt() {
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
        assertEquals(DockTarget.OnDockable(DockableId(MAIN_WINDOW_NAME)), first.target)
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
