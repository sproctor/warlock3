package warlockfe.warlock3.compose.ui.game

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import co.touchlab.kermit.Logger
import com.seanproctor.docking.layout.dockLayout
import com.seanproctor.docking.model.DockLayout
import com.seanproctor.docking.model.DockNode
import com.seanproctor.docking.model.DockRegion
import com.seanproctor.docking.model.DockableId
import com.seanproctor.docking.model.DockableOptions
import com.seanproctor.docking.model.SplitOrientation
import com.seanproctor.docking.persistence.DockingPersistence
import com.seanproctor.docking.persistence.captureLayout
import com.seanproctor.docking.persistence.restoreLayout
import com.seanproctor.docking.state.DockState
import com.seanproctor.docking.state.DockTarget
import com.seanproctor.docking.state.DockableSpec
import com.seanproctor.docking.state.DockingEvent
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import warlockfe.warlock3.compose.ui.window.WindowUiState
import warlockfe.warlock3.core.window.WindowLocation
import kotlin.time.Duration.Companion.seconds

const val MAIN_WINDOW_NAME = "main"

// A side column's share of the window when it opens at the root, and a top/bottom band's share of
// the main text area: rough ports of the old fixed-dp docks into split proportions. Only the
// default for a window with no saved dock spot; from then on the layout JSON remembers.
private const val SIDE_COLUMN_PROPORTION = 0.2f
private const val BAND_PROPORTION = 0.25f

// How long to wait for the character to be identified before giving up on a saved dock layout,
// matching the gates in GameViewModel that hold game-driven opens for the same reason.
private val CHARACTER_WAIT = 10.seconds

/**
 * One open game window as the docking bridge sees it. [location] is the dock the game's
 * announcement suggests, read live from the window info (announcements can arrive after the
 * window opens); it seeds the default spot for a window the saved layout does not know.
 */
data class OpenGameWindow(
    val uiState: WindowUiState,
) {
    val name: String get() = uiState.name

    val location: WindowLocation
        get() =
            if (name == MAIN_WINDOW_NAME) {
                WindowLocation.MAIN
            } else {
                uiState.windowInfo.value
                    ?.location
                    ?: WindowLocation.TOP
            }
}

/**
 * The docking layout for a game connection, bridging [GameViewModel]'s window state into a
 * compose-docking [DockState].
 *
 * The view model stays the authority on which windows are open (the game and the sidebar open and
 * close them); the dock state owns only their arrangement. The bridge reconciles the two: a window
 * appearing in the view model's open list is docked (at its spot in the saved layout JSON when it
 * has one, else at a default derived from its game-announced location), and one disappearing is
 * undocked.
 * Closing from the dock's own chrome routes through [GameViewModel.closeWindow], so the window is
 * remembered hidden exactly as the old header close button did.
 *
 * The arrangement persists per character as JSON under [DOCK_LAYOUT_KEY], restored once the
 * character is identified and saved debounced on every change.
 *
 * [trailingActions] renders the title-bar buttons for a docked window (the library draws none of
 * its own); [windowContent] renders the window body, headerless, with the platform's window view.
 */
@OptIn(FlowPreview::class)
@Composable
fun rememberGameDockState(
    viewModel: GameViewModel,
    trailingActions: @Composable (state: DockState, window: OpenGameWindow) -> Unit,
    windowContent: @Composable (window: OpenGameWindow) -> Unit,
): DockState {
    val main by viewModel.mainWindowUiState.collectAsState()
    val windows by viewModel.windowUiStates.collectAsState()

    // Insertion-ordered: main first, then the view model's restore/open order.
    val openWindows: Map<String, OpenGameWindow> =
        buildMap {
            put(main.name, OpenGameWindow(main))
            windows.forEach { put(it.name, OpenGameWindow(it)) }
        }
    val currentOpenWindows: State<Map<String, OpenGameWindow>> = rememberUpdatedState(openWindows)

    // Snapshot-state mirror read by the dockable specs' lambdas (title, actions, content), so a
    // registered dockable keeps rendering the live ui state without re-registration.
    val windowsByName = remember { mutableStateMapOf<String, OpenGameWindow>() }
    SideEffect {
        windowsByName.keys.retainAll(openWindows.keys)
        openWindows.forEach { (name, window) ->
            if (windowsByName[name] != window) {
                windowsByName[name] = window
            }
        }
    }

    val currentTrailingActions by rememberUpdatedState(trailingActions)
    val currentWindowContent by rememberUpdatedState(windowContent)

    val state =
        remember {
            DockState(
                initialLayout =
                    dockLayout {
                        mainWindow {
                            dock(MAIN_WINDOW_NAME)
                        }
                    },
            )
        }

    fun registerWindow(name: String) {
        val id = DockableId(name)
        if (state.registry.isRegistered(id)) return
        state.registry.register(
            DockableSpec(
                id = id,
                options =
                    DockableOptions(
                        closable = name != MAIN_WINDOW_NAME,
                        // Floating OS windows need application-scope wiring the game screen does
                        // not have yet; keep every window inside the main window for now.
                        floatable = false,
                    ),
                title = {
                    val info =
                        windowsByName[name]
                            ?.uiState
                            ?.windowInfo
                            ?.value
                    (info?.title ?: name) + (info?.subtitle ?: "")
                },
                canClose = {
                    // The dock close button is the old header close button: remember the window
                    // hidden so the game cannot reopen it unasked.
                    viewModel.closeWindow(name)
                    true
                },
                trailingActions = {
                    windowsByName[name]?.let { currentTrailingActions(state, it) }
                },
                content = {
                    windowsByName[name]?.let { currentWindowContent(it) }
                },
            ),
        )
    }

    DisposableEffect(state) {
        registerWindow(MAIN_WINDOW_NAME)
        onDispose {
            state.registry.unregister(DockableId(MAIN_WINDOW_NAME))
        }
    }

    // The dock's focus follows into the view model's selected window (scroll macros and find act
    // on it), alongside the in-content press handler that was already there.
    LaunchedEffect(state, viewModel) {
        snapshotFlow { state.activeDockable }
            .filterNotNull()
            .collect { viewModel.selectWindow(it.value) }
    }

    LaunchedEffect(state, viewModel) {
        // Restore the saved arrangement once the character is known. Until then the only window on
        // screen is main: the view model's own restore gates every other open on the character id.
        val characterId =
            withTimeoutOrNull(CHARACTER_WAIT) {
                viewModel.connectedCharacterId.filterNotNull().first()
            }
        if (characterId != null) {
            val saved = runCatching { viewModel.loadDockLayout() }.getOrNull()
            if (saved != null) {
                runCatching { DockingPersistence.decode(saved) }
                    .onSuccess { state.restoreLayout(it, mergeFloatingWindows = true) }
                    .onFailure { Logger.w(it) { "Discarding unreadable dock layout" } }
            }
        }
        // A restored layout retains a leaf for every window it remembers, including ones that are
        // closed right now; reconcile immediately so they never render as placeholders.
        reconcile(state, currentOpenWindows.value, ::registerWindow)
        coroutineScope {
            launch {
                merge(
                    snapshotFlow { currentOpenWindows.value }.map { },
                    // A drop can re-dock a window the game closed mid-drag; a Docked event is the
                    // only signal (the open set did not change), so reconcile on those too.
                    state.events
                        .filterIsInstance<DockingEvent.Docked>()
                        .filter { !it.isTemporary }
                        .map { },
                ).collect {
                    reconcile(state, currentOpenWindows.value, ::registerWindow)
                }
            }
            if (characterId != null) {
                launch {
                    snapshotFlow { state.layout }
                        .drop(1)
                        .debounce(1.seconds)
                        .collect {
                            viewModel.saveDockLayout(DockingPersistence.encode(state.captureLayout()))
                        }
                }
            }
        }
    }

    return state
}

/**
 * Brings the dock state in line with the view model's open windows: registers and docks the ones
 * that opened, undocks the ones that closed. Never touches a window that is both open and docked,
 * so a user arrangement is left alone.
 */
internal fun reconcile(
    state: DockState,
    openWindows: Map<String, OpenGameWindow>,
    registerWindow: (String) -> Unit,
) {
    dockedIds(state.layout)
        .filter { it.value != MAIN_WINDOW_NAME && it.value !in openWindows }
        .forEach { state.undock(it) }
    openWindows.forEach { (name, window) ->
        registerWindow(name)
        val id = DockableId(name)
        if (!state.isOpen(id)) {
            val placement =
                defaultPlacement(
                    name = name,
                    location = window.location,
                    layout = state.layout,
                    openWindows = openWindows,
                )
            state.dock(id, placement.target, placement.region, placement.proportion)
        }
    }
}

/** Where [reconcile] puts a window with no saved dock spot. */
internal data class DockPlacement(
    val target: DockTarget,
    val region: DockRegion,
    val proportion: Float,
)

/**
 * The default spot for a window, derived from the dock the game's announcement suggests. The
 * location names a region relative to the main text window (left/right columns, top/bottom bands);
 * the window joins that region as it exists *now* - stacked onto the last open window currently on
 * that side of main (columns stack downward, bands extend rightward) - and when the region is
 * empty it opens it with a fresh split: side columns at the window root, outside everything, and
 * top/bottom bands directly off the main window.
 *
 * Membership in a region is decided by where a window sits in the tree today, not by the location
 * it was seeded from: once the user drags a window somewhere else, it stops attracting its old
 * dock-mates there (and a window dragged into a region becomes part of it). The main window itself
 * is never a region member; it is the reference point the regions are measured against.
 */
internal fun defaultPlacement(
    name: String,
    location: WindowLocation,
    layout: DockLayout,
    openWindows: Map<String, OpenGameWindow>,
): DockPlacement {
    val mainWindow = layout.mainWindow
    val tree = mainWindow.maximized?.savedRoot ?: mainWindow.root
    val mainId = DockableId(MAIN_WINDOW_NAME)
    val regionOfLocation =
        when (location) {
            WindowLocation.LEFT -> DockRegion.West
            WindowLocation.RIGHT -> DockRegion.East
            WindowLocation.TOP -> DockRegion.North
            WindowLocation.BOTTOM -> DockRegion.South
            WindowLocation.MAIN -> null
        }
    val neighbor =
        if (tree != null && regionOfLocation != null) {
            openWindows.keys.lastOrNull { other ->
                other != name &&
                    other != MAIN_WINDOW_NAME &&
                    relativeRegion(tree, DockableId(other), mainId) == regionOfLocation
            }
        } else {
            null
        }
    if (neighbor != null) {
        val region =
            when (location) {
                WindowLocation.LEFT, WindowLocation.RIGHT -> DockRegion.South
                else -> DockRegion.East
            }
        return DockPlacement(DockTarget.OnDockable(DockableId(neighbor)), region, 0.5f)
    }
    // Nothing in the region yet: open it with a split directly off the main text window (or the
    // window root when main is somehow absent, which the next reconcile pass heals).
    val mainTarget =
        if (tree?.containsDockable(mainId) == true) {
            DockTarget.OnDockable(mainId)
        } else {
            DockTarget.Root()
        }
    return when (location) {
        // Side columns open at the window root, outside everything else, running the full height
        // rather than sitting inside the center column.
        WindowLocation.LEFT -> DockPlacement(DockTarget.Root(), DockRegion.West, SIDE_COLUMN_PROPORTION)

        WindowLocation.RIGHT -> DockPlacement(DockTarget.Root(), DockRegion.East, SIDE_COLUMN_PROPORTION)

        WindowLocation.TOP -> DockPlacement(mainTarget, DockRegion.North, BAND_PROPORTION)

        WindowLocation.BOTTOM -> DockPlacement(mainTarget, DockRegion.South, BAND_PROPORTION)

        WindowLocation.MAIN -> DockPlacement(DockTarget.Root(), DockRegion.Center, BAND_PROPORTION)
    }
}

/**
 * The side of [b] that [a] currently sits on in [root]: the orientation of their lowest common
 * ancestor split, read from whichever child holds [a] (a horizontal split's first child is west,
 * a vertical split's first child is north). Null when either is absent from the tree or they share
 * a tab group (no direction between tabs).
 */
internal fun relativeRegion(
    root: DockNode,
    a: DockableId,
    b: DockableId,
): DockRegion? =
    when (root) {
        is DockNode.Leaf, is DockNode.Anchor -> {
            null
        }

        is DockNode.Tabs -> {
            null
        }

        is DockNode.Split -> {
            val aInFirst = root.first.containsDockable(a)
            val bInFirst = root.first.containsDockable(b)
            val aInSecond = !aInFirst && root.second.containsDockable(a)
            val bInSecond = !bInFirst && root.second.containsDockable(b)
            when {
                aInFirst && bInSecond -> {
                    if (root.orientation == SplitOrientation.Horizontal) DockRegion.West else DockRegion.North
                }

                aInSecond && bInFirst -> {
                    if (root.orientation == SplitOrientation.Horizontal) DockRegion.East else DockRegion.South
                }

                aInFirst && bInFirst -> {
                    relativeRegion(root.first, a, b)
                }

                aInSecond && bInSecond -> {
                    relativeRegion(root.second, a, b)
                }

                else -> {
                    null
                }
            }
        }
    }

/** Every dockable present in [layout], including those parked behind a maximized sibling. */
private fun dockedIds(layout: DockLayout): List<DockableId> =
    layout.windows
        .flatMap { window ->
            listOfNotNull(window.root, window.maximized?.savedRoot)
                .flatMap { it.dockableIds() }
        }.distinct()

private fun DockNode.dockableIds(): List<DockableId> =
    when (this) {
        is DockNode.Leaf -> listOf(dockableId)
        is DockNode.Split -> first.dockableIds() + second.dockableIds()
        is DockNode.Tabs -> tabs.map { it.dockableId }
        is DockNode.Anchor -> emptyList()
    }

private fun DockNode.containsDockable(id: DockableId): Boolean = id in dockableIds()
