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
import androidx.compose.ui.unit.dp
import co.touchlab.kermit.Logger
import com.seanproctor.docking.layout.dockLayout
import com.seanproctor.docking.model.AnchorId
import com.seanproctor.docking.model.DockLayout
import com.seanproctor.docking.model.DockNode
import com.seanproctor.docking.model.DockRegion
import com.seanproctor.docking.model.DockableId
import com.seanproctor.docking.model.DockableOptions
import com.seanproctor.docking.model.SplitOrientation
import com.seanproctor.docking.model.WindowId
import com.seanproctor.docking.persistence.DockingPersistence
import com.seanproctor.docking.persistence.captureLayout
import com.seanproctor.docking.persistence.restoreLayout
import com.seanproctor.docking.state.DockState
import com.seanproctor.docking.state.DockTarget
import com.seanproctor.docking.state.DockableSpec
import com.seanproctor.docking.state.DockingEvent
import com.seanproctor.docking.state.DockingSettings
import com.seanproctor.docking.state.EmptyAnchorVisibility
import com.seanproctor.docking.ui.platformDockCapabilities
import kotlinx.coroutines.CompletableDeferred
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

// How wide an emptied dock area is while a drag is in flight. It is invisible the rest of the
// time, so this only has to be a comfortable target to drop on, not a small price to pay.
private val EMPTY_AREA_STRIP = 24.dp

// How long the first layout pass holds out for the saved dock layout before laying windows out
// anyway, matching the gates in GameViewModel that hold game-driven opens for the same reason.
// Only the initial wait is bounded: the restore itself (and the save loop behind it) waits for
// the character however long identification takes.
private val CHARACTER_WAIT = 10.seconds

// The three areas windows are arranged into, as compose-docking anchors. An anchor is a named slot:
// when the last window carrying it leaves, the library restores a placeholder in its place instead
// of collapsing the split, so an area the user empties is still there for the next window that
// wants it - which lands back in the same spot, at the same size. Since the placeholder is hidden
// until a drag (see the DockState settings below), an emptied area is invisible until the user
// picks something up, and only then offers itself as a strip to drop on.
//
// All three are declared in the initial layout, so a character who has never opened a side window
// still has somewhere to drop one: pick a window up and the empty areas offer themselves. That is
// only affordable because they are invisible until then - at full size it would mean two empty
// columns and a band on every game screen.
private val LEFT_ANCHOR = AnchorId("left")
private val CENTER_ANCHOR = AnchorId("center")
private val RIGHT_ANCHOR = AnchorId("right")

/** The area a window announced for [this] belongs to. */
internal val WindowLocation.anchor: AnchorId
    get() =
        when (this) {
            WindowLocation.LEFT -> LEFT_ANCHOR

            WindowLocation.RIGHT -> RIGHT_ANCHOR

            // We dock rather than float, so the locations the real client floats (and the chrome
            // ones, which never reach the dock) share the band above main with the room window.
            WindowLocation.CENTER,
            WindowLocation.DETACHED,
            WindowLocation.STATBAR,
            WindowLocation.QUICKBAR,
            -> CENTER_ANCHOR
        }

/**
 * The empty game layout: the main text window with the three areas declared around it. Built
 * innermost outwards, so the band sits above main inside the centre column and the two side
 * columns wrap the whole thing and run the full height.
 *
 * Declaring the areas up front is what lets all three offer themselves during a drag on a layout
 * that has never used them, and it is only affordable because an empty one is invisible until then
 * - at full size it would mean two empty columns and a band on every game screen.
 */
internal fun gameDockLayout(): DockLayout =
    dockLayout {
        mainWindow {
            dock(MAIN_WINDOW_NAME)
            anchorAtRoot(CENTER_ANCHOR.value, DockRegion.North, BAND_PROPORTION)
            anchorAtRoot(LEFT_ANCHOR.value, DockRegion.West, SIDE_COLUMN_PROPORTION)
            anchorAtRoot(RIGHT_ANCHOR.value, DockRegion.East, SIDE_COLUMN_PROPORTION)
        }
    }

/**
 * The windows to reconcile against, keyed by name. Insertion-ordered: main first, then the view
 * model's restore/open order. A window the game put in the client's own chrome is drawn there and
 * never docked, so it is left out - which also undocks one an older build's saved layout still
 * names.
 */
internal fun openGameWindows(
    main: WindowUiState,
    windows: List<WindowUiState>,
): Map<String, OpenGameWindow> =
    buildMap {
        put(main.name, OpenGameWindow(main))
        windows
            .map { OpenGameWindow(it) }
            .filterNot { it.location.isChrome }
            .forEach { put(it.name, it) }
    }

/**
 * One open game window as the docking bridge sees it. [location] is where the game's announcement
 * asks for, read live from the window info (announcements can arrive after the window opens); it
 * seeds the default spot for a window the saved layout does not know.
 *
 * A window the game did not place goes to [WindowLocation.CENTER], which is the fallback the real
 * client uses for the same three cases (no location, chrome bars we draw ourselves, a location it
 * rejects). The main text window is not placed from a location at all; it is the fixed point the
 * others are arranged around, so [isMain] answers for it instead.
 */
data class OpenGameWindow(
    val uiState: WindowUiState,
) {
    val name: String get() = uiState.name

    val isMain: Boolean get() = name == MAIN_WINDOW_NAME

    val location: WindowLocation
        get() =
            uiState.windowInfo.value
                ?.location
                ?: WindowLocation.CENTER
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

    val openWindows: Map<String, OpenGameWindow> = openGameWindows(main, windows)
    val currentOpenWindows: State<Map<String, OpenGameWindow>> = rememberUpdatedState(openWindows)

    // The open set as the view model has it right now, rather than as of the last composition.
    // Reconciles run from coroutines that can outpace recomposition - the first one after a restore
    // reliably does - and reconciling against a stale, still-empty set undocks the whole restored
    // arrangement. The composition-lagged state above stays as the recomposition trigger only.
    fun liveOpenWindows(): Map<String, OpenGameWindow> = openGameWindows(viewModel.mainWindowUiState.value, viewModel.windowUiStates.value)

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
                // An emptied area holds its slot open so the next window lands back in it, but a
                // game screen should not carry a visible reminder of a column the user just
                // cleared. Hidden until a drag, the area costs nothing at all; picking up any
                // window brings its strip back to aim at, and dropping there restores the column
                // at the size it was.
                settings =
                    DockingSettings(
                        collapsedAnchorThickness = EMPTY_AREA_STRIP,
                        emptyAnchorVisibility = EmptyAnchorVisibility.WhileDragging,
                    ),
                initialLayout = gameDockLayout(),
            )
        }

    // Re-registers when the anchor changes, which is how a window that moved between areas takes its
    // new one: the registry is where the library reads a dockable's anchor from, so overwriting the
    // spec is the whole update. Registration is otherwise idempotent.
    fun registerWindow(
        name: String,
        anchor: AnchorId?,
    ) {
        val id = DockableId(name)
        if (state.registry[id]?.options?.anchor == anchor && state.registry.isRegistered(id)) return
        state.registry.register(
            DockableSpec(
                id = id,
                options =
                    DockableOptions(
                        closable = name != MAIN_WINDOW_NAME,
                        // Any window but main can be torn off into its own OS window, on a platform
                        // that has OS windows to tear it into - mobile does not, and the library
                        // gates its own drag-out gesture on the same capability flag.
                        //
                        // Main is excluded because it is the fixed point the areas are measured
                        // against: currentAnchorOf reads a window's area from which side of main it
                        // sits on, so floating main away would leave every other window reading as
                        // the centre and scramble the arrangement on the next reconcile.
                        floatable = name != MAIN_WINDOW_NAME && platformDockCapabilities.floatingWindows,
                        anchor = anchor,
                    ),
                title = {
                    val info =
                        windowsByName[name]
                            ?.uiState
                            ?.windowInfo
                            ?.value
                    // A subtitle arrives from the protocol with its separator baked in (" - [...]"),
                    // so plain concatenation renders the title bar the real client shows.
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
        // Main carries no anchor: it belongs to no area, and a carrier would stop the center's
        // placeholder from ever being restored.
        registerWindow(MAIN_WINDOW_NAME, anchor = null)
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
        val restoreFinished = CompletableDeferred<Unit>()
        coroutineScope {
            // Restore the saved arrangement once the character is known, however long
            // identification takes, and keep every later change saved. A session that never
            // identifies a character has nothing to restore from or save under.
            launch {
                viewModel.connectedCharacterId.filterNotNull().first()
                // The view model restores the character's open windows off that same emission, and
                // nothing orders the two. Wait for its list before reconciling: the reconcile below
                // undocks every window the saved arrangement names that is not open, so running it
                // against a list that has not been filled in yet throws the arrangement away and
                // then saves the wreckage over it a second later. Bounded like the gate below, so a
                // restore that never finishes cannot strand the layout unrestored.
                if (withTimeoutOrNull(CHARACTER_WAIT) { viewModel.awaitWindowsRestored() } == null) {
                    Logger.w { "Window restore still running after $CHARACTER_WAIT; docking against it" }
                }
                val saved = runCatching { viewModel.loadDockLayout() }.getOrNull()
                applyRestoredLayout(state, saved, liveOpenWindows(), ::registerWindow)
                restoreFinished.complete(Unit)
                snapshotFlow { state.layout }
                    .drop(1)
                    .debounce(1.seconds)
                    .collect {
                        viewModel.saveDockLayout(DockingPersistence.encode(state.captureLayout()))
                    }
            }
            // Hold the first layout pass for the restore, so early windows do not flash into
            // default spots only to be replaced - but only for a bounded moment, so a session
            // whose character is identified slowly (or never) still lays out. A restore landing
            // after the timeout reconciles again above.
            launch {
                withTimeoutOrNull(CHARACTER_WAIT) { restoreFinished.await() }
                reconcile(state, liveOpenWindows(), ::registerWindow)
                merge(
                    snapshotFlow { currentOpenWindows.value }.map { },
                    // A drop can re-dock a window the game closed mid-drag; a Docked event is the
                    // only signal (the open set did not change), so reconcile on those too.
                    state.events
                        .filterIsInstance<DockingEvent.Docked>()
                        .filter { !it.isTemporary }
                        .map { },
                ).collect {
                    reconcile(state, liveOpenWindows(), ::registerWindow)
                }
            }
            // A detached window's own close button is the OS one, which drops the window's contents
            // from the layout without going through the close veto the dock header's button does.
            // The view model would still have them open, and the next reconcile pass would dock them
            // straight back into the main window - closing a detached window would look like it
            // jumped back into the dock. Route them through the view model instead, so it hides them
            // exactly as closing a docked window does.
            //
            // A floating window also disappears when its last window is dragged back into the dock,
            // which is indistinguishable from here. The still-docked check tells the two apart: a
            // dragged-out window is somewhere else in the layout by the time it settles, a closed one
            // is nowhere at all.
            launch {
                var detached = emptyMap<WindowId, List<DockableId>>()
                snapshotFlow { state.layout }.collect { layout ->
                    val current =
                        layout.floatingWindows.associate { window ->
                            window.id to
                                listOfNotNull(window.root, window.maximized?.savedRoot)
                                    .flatMap { it.dockableIds() }
                        }
                    (detached.keys - current.keys).forEach { closed ->
                        detached[closed]
                            ?.filterNot { state.isOpen(it) }
                            ?.forEach { viewModel.closeWindow(it.value) }
                    }
                    detached = current
                }
            }
        }
    }

    return state
}

/**
 * Puts the character's saved arrangement back: decodes [saved] over the layout, restores any area
 * it does not carry, and reconciles the result against [openWindows].
 *
 * [openWindows] must be the character's restored window list, not an empty or partial one.
 * [reconcile] cannot tell a window the user closed from one that has not been restored yet, so it
 * undocks everything the arrangement names that is not in the set - and the debounced save then
 * writes the wreckage over the arrangement a second later. That is why the caller waits on
 * [GameViewModel.awaitWindowsRestored] before getting here.
 */
internal fun applyRestoredLayout(
    state: DockState,
    saved: String?,
    openWindows: Map<String, OpenGameWindow>,
    registerWindow: (String, AnchorId?) -> Unit,
) {
    if (saved != null) {
        runCatching { DockingPersistence.decode(saved) }
            .onSuccess {
                state.restoreLayout(
                    it,
                    // Detached windows come back detached, at the size and screen position they
                    // were left at - the layout JSON carries both. On a platform with no OS windows
                    // to put them back in, their contents are grafted into the main window instead,
                    // which is also how a layout detached on the desktop opens on a phone.
                    mergeFloatingWindows = !platformDockCapabilities.floatingWindows,
                )
            }.onFailure { Logger.w(it) { "Discarding unreadable dock layout" } }
    }
    // A restore replaces the layout the builder made, so a character whose JSON was saved before
    // the areas were declared comes back without them - and would have nowhere to drop a window.
    // Put back whichever are missing; each call is a no-op when the area is already there, whether
    // as a placeholder or as windows the character has in it.
    state.ensureAnchor(LEFT_ANCHOR, DockRegion.West, SIDE_COLUMN_PROPORTION)
    state.ensureAnchor(RIGHT_ANCHOR, DockRegion.East, SIDE_COLUMN_PROPORTION)
    state.ensureAnchor(CENTER_ANCHOR, DockRegion.North, BAND_PROPORTION)
    // A restored layout retains a leaf for every window it remembers, including ones that are
    // closed right now; reconcile immediately so they never render as placeholders. This also
    // re-docks anything a late restore displaced, when the bounded wait in the bridge gave up and
    // laid windows out first.
    reconcile(state, openWindows, registerWindow)
}

/**
 * Brings the dock state in line with the view model's open windows: registers and docks the ones
 * that opened, undocks the ones that closed. Never touches a window that is both open and docked,
 * so a user arrangement is left alone.
 *
 * Registration also keeps each window's anchor pointed at the area it is in right now, which is why
 * this runs on every drop as well as on every open and close: the anchor a docked window carries is
 * the area it sits in, and only a window that is not docked yet falls back to the one its
 * announcement asked for. That is what makes the placeholder the library restores land where the
 * user last had the window, rather than where the game first put it.
 */
internal fun reconcile(
    state: DockState,
    openWindows: Map<String, OpenGameWindow>,
    registerWindow: (String, AnchorId?) -> Unit,
) {
    dockedIds(state.layout)
        .filter { it.value != MAIN_WINDOW_NAME && it.value !in openWindows }
        .forEach { state.undock(it) }
    val mainWindow = state.layout.mainWindow
    val tree = mainWindow.maximized?.savedRoot ?: mainWindow.root
    openWindows.forEach { (name, window) ->
        val anchor =
            when {
                name == MAIN_WINDOW_NAME -> null

                // A detached window is alone in a window of its own, where the area it came from
                // means nothing. Carrying the anchor there would have the library hold the area's
                // slot open inside the detached window when the window leaves it - a placeholder
                // for a column that is not there, keeping an empty OS window on screen. It takes
                // its area back from the tree the moment it is docked in the main window again.
                isDetached(state, name) -> null

                else -> tree?.let { currentAnchorOf(it, name) } ?: window.location.anchor
            }
        registerWindow(name, anchor)
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
    closeEmptyDetachedWindows(state)
}

/**
 * Closes detached windows with no game window left in them.
 *
 * A window still carries its area anchor at the moment it is torn off, so the first window to leave
 * a detached window can leave an area placeholder behind in it, and a window holding nothing but a
 * placeholder is an empty frame on screen that the user cannot get anything back into. [reconcile]
 * stops the anchor being carried once a window is detached, which heads this off for everything
 * afterwards; this covers the gap before that pass has run - reattaching a window the instant it was
 * detached, in particular.
 */
internal fun closeEmptyDetachedWindows(state: DockState) {
    state.layout.floatingWindows
        .filter { window -> window.root?.dockableIds().isNullOrEmpty() }
        .forEach { state.closeWindow(it.id) }
}

/**
 * Tears [name] off into its own OS window. A no-op where the platform has no floating windows, or
 * for a window that is not floatable (main).
 *
 * The library sizes and places the new window itself when it is not told where to put one, which is
 * what this wants: a window detached from a menu has no drag to take a position from, unlike one
 * dragged out of the dock.
 */
internal fun detachWindow(
    state: DockState,
    name: String,
) {
    state.moveToNewWindow(DockableId(name))
}

/**
 * Puts a detached window back into the main window, in the spot a window of its kind would open in.
 *
 * That is [defaultPlacement], the same rule [reconcile] uses for a window with no saved dock spot,
 * so reattaching lands the window back in its announced area - filling the area's empty slot if the
 * user left one, or stacking onto whatever is already there. A detached window sits in no area (it
 * is not in the main window's tree to be measured against main at all), so there is no arrangement
 * to preserve and the announced spot is the best answer available.
 */
internal fun redockIntoMainWindow(
    state: DockState,
    window: OpenGameWindow,
    openWindows: Map<String, OpenGameWindow>,
) {
    val placement =
        defaultPlacement(
            name = window.name,
            location = window.location,
            layout = state.layout,
            openWindows = openWindows,
        )
    state.dock(DockableId(window.name), placement.target, placement.region, placement.proportion)
    closeEmptyDetachedWindows(state)
}

/** Whether [name] is currently in a detached window rather than the main one. */
internal fun isDetached(
    state: DockState,
    name: String,
): Boolean {
    val window = state.windowOf(DockableId(name))
    return window != null && window != WindowId.MAIN
}

/** Where [reconcile] puts a window with no saved dock spot. */
internal data class DockPlacement(
    val target: DockTarget,
    val region: DockRegion,
    val proportion: Float,
)

/**
 * The default spot for a window, derived from the area its announcement asks for. Three cases, in
 * order:
 *
 * 1. The area is holding a placeholder, because the user emptied it earlier. [DockTarget.Anchor]
 *    drops the window straight into that slot, so the area reopens exactly where it was.
 * 2. The area has windows in it. The new one stacks onto the last of them (columns stack downward,
 *    the band above main extends rightward), which is how the real client fills its panel columns.
 * 3. The area is missing from the tree altogether, which now only happens to a layout saved before
 *    the areas were declared up front. It opens with a fresh split: side columns at the window
 *    root, outside everything, and the band directly off the main window.
 *
 * Which windows are "in" an area is read from where they sit in the tree right now, not from the
 * location they were seeded with: drag a window out of the left column and it stops attracting its
 * old dock-mates there, while one dragged in starts attracting them. [reconcile] keeps each
 * window's registered anchor in step with that, so the placeholder the library restores is the one
 * for the area the user last had the window in. The main window belongs to no area; it is the fixed
 * point the areas are measured against, so it takes the center and ignores [location] entirely.
 */
internal fun defaultPlacement(
    name: String,
    location: WindowLocation,
    layout: DockLayout,
    openWindows: Map<String, OpenGameWindow>,
): DockPlacement {
    if (name == MAIN_WINDOW_NAME) {
        return DockPlacement(DockTarget.Root(), DockRegion.Center, BAND_PROPORTION)
    }
    val mainWindow = layout.mainWindow
    val tree = mainWindow.maximized?.savedRoot ?: mainWindow.root
    val mainId = DockableId(MAIN_WINDOW_NAME)
    val anchor = location.anchor
    if (tree != null && tree.holdsAnchor(anchor)) {
        // Region and proportion are ignored for an anchor target: the placeholder names the slot.
        return DockPlacement(DockTarget.Anchor(anchor), DockRegion.Center, BAND_PROPORTION)
    }
    val neighbors =
        if (tree != null) {
            openWindows.keys.filter { other ->
                other != name && other != MAIN_WINDOW_NAME && currentAnchorOf(tree, other) == anchor
            }
        } else {
            emptyList()
        }
    if (tree != null && neighbors.isNotEmpty()) {
        val region =
            when (anchor) {
                LEFT_ANCHOR, RIGHT_ANCHOR -> DockRegion.South
                else -> DockRegion.East
            }
        // One of N, where N counts the area's windows once this one has joined them. The library
        // reads a proportion as the newcomer's share, so splitting the area as a whole hands it
        // exactly that and leaves the rest of the area to divide what remains in the ratio it
        // already had - which is what makes a column of windows opened this way come out even, and
        // what keeps a column the user has since adjusted in the proportions they chose.
        //
        // Splitting the area's *last window* instead is what made the first window twice the size
        // of the second, four times the third, and so on: each newcomer halved whatever it landed
        // on rather than taking a share of the whole.
        val share = 1f / (neighbors.size + 1)
        val areaNode = tree.smallestNodeHolding(neighbors.map { DockableId(it) }.toSet())
        if (areaNode != null) {
            return DockPlacement(DockTarget.OnNode(mainWindow.id, areaNode.id), region, share)
        }
        // The area is scattered rather than sitting in a subtree of its own, so there is nothing to
        // split as a whole. Stack on the last of them, still at the newcomer's proper share.
        return DockPlacement(DockTarget.OnDockable(DockableId(neighbors.last())), region, share)
    }
    // Nothing in the area yet: open it with a split directly off the main text window (or the
    // window root when main is somehow absent, which the next reconcile pass heals).
    val mainTarget =
        if (tree?.containsDockable(mainId) == true) {
            DockTarget.OnDockable(mainId)
        } else {
            DockTarget.Root()
        }
    return when (anchor) {
        // Side columns open at the window root, outside everything else, running the full height
        // rather than sitting inside the center column.
        LEFT_ANCHOR -> DockPlacement(DockTarget.Root(), DockRegion.West, SIDE_COLUMN_PROPORTION)

        RIGHT_ANCHOR -> DockPlacement(DockTarget.Root(), DockRegion.East, SIDE_COLUMN_PROPORTION)

        else -> DockPlacement(mainTarget, DockRegion.North, BAND_PROPORTION)
    }
}

/**
 * The area [name] currently sits in, read from its position relative to the main window, or null
 * when it is not docked. This is what makes an area the user's arrangement rather than the game's:
 * a window dragged from one column to the other changes areas by being moved, with no announcement
 * involved.
 */
internal fun currentAnchorOf(
    tree: DockNode,
    name: String,
): AnchorId? {
    val id = DockableId(name)
    if (!tree.containsDockable(id)) return null
    return when (relativeRegion(tree, id, DockableId(MAIN_WINDOW_NAME))) {
        DockRegion.West -> LEFT_ANCHOR

        DockRegion.East -> RIGHT_ANCHOR

        // North, South and tabbed-with-main (no direction between tabs) all read as the center.
        else -> CENTER_ANCHOR
    }
}

/** Whether [anchor]'s placeholder is holding a slot open in this tree. */
internal fun DockNode.holdsAnchor(anchor: AnchorId): Boolean =
    when (this) {
        is DockNode.Anchor -> anchorId == anchor
        is DockNode.Split -> first.holdsAnchor(anchor) || second.holdsAnchor(anchor)
        is DockNode.Leaf, is DockNode.Tabs -> false
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

internal fun DockNode.containsDockable(id: DockableId): Boolean = id in dockableIds()

/**
 * The smallest subtree holding exactly [ids] and nothing else, or null when no such subtree exists.
 *
 * This is how an area is addressed as a unit. The windows of an area normally sit together in one
 * subtree - the left column is a subtree, the band above main is a subtree - so the newcomer can
 * split that, take its share of the whole, and leave the windows already there dividing the rest
 * between them as before.
 *
 * Exactly, because a subtree holding the area *and* something else is not the area: splitting the
 * node that holds both a side column and the main text window would put the newcomer alongside the
 * whole screen rather than in the column it asked for. A layout dragged into that shape has no area
 * to split and the caller stacks instead.
 */
private fun DockNode.smallestNodeHolding(ids: Set<DockableId>): DockNode? {
    if (ids.isEmpty() || !ids.all { containsDockable(it) }) return null
    if (this is DockNode.Split) {
        first.smallestNodeHolding(ids)?.let { return it }
        second.smallestNodeHolding(ids)?.let { return it }
    }
    return takeIf { dockableIds().toSet() == ids }
}
