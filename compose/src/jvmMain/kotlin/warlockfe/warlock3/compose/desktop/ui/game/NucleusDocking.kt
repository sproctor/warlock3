@file:OptIn(ExperimentalNucleusApi::class, ExperimentalFoundationApi::class)

package warlockfe.warlock3.compose.desktop.ui.game

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.seanproctor.docking.model.WindowId
import com.seanproctor.docking.spi.LocalDockingRenderer
import com.seanproctor.docking.state.DockState
import com.seanproctor.docking.ui.DockArea
import com.seanproctor.docking.window.DockDragGrip
import com.seanproctor.docking.window.DockHostWindow
import com.seanproctor.docking.window.DockingDragHandover
import com.seanproctor.docking.window.LocalDockingDragHandover
import com.seanproctor.docking.window.dockWindowDragArea
import com.seanproctor.docking.window.hostWindow
import com.seanproctor.docking.window.registerDockingWindow
import com.seanproctor.docking.window.windowHost
import dev.nucleusframework.application.DecoratedWindow
import dev.nucleusframework.application.LocalNucleusWindow
import dev.nucleusframework.application.NucleusApplicationScope
import dev.nucleusframework.application.NucleusWindow
import dev.nucleusframework.window.ExperimentalNucleusApi
import dev.nucleusframework.window.tao.workspace.TransferDrag
import dev.nucleusframework.window.tao.workspace.TransferDragGesture
import dev.nucleusframework.window.tao.workspace.TransferGhostSource
import dev.nucleusframework.window.tao.workspace.positionInWindowPx
import dev.nucleusframework.window.tao.workspace.transferDragHandle
import kotlin.math.roundToInt

// The docking library's window API, answered by Nucleus's Tao windows. Adapted from the Tao demo in
// compose-docking, which is where this integration is maintained: the library knows nothing of
// Nucleus, so each application wires its own windows to it.

/**
 * A Nucleus window as the docking system sees it. Nucleus reports logical screen units, the ones
 * every window shares, so they pass through unscaled.
 *
 * Nucleus gives only the outer bounds, decorations included, so they stand for the content's: true
 * for Warlock's windows, whose title bars are drawn in Compose inside the content. The read and the
 * move both use the outer position, so a window dragged by its title bar follows the pointer
 * either way.
 */
private class NucleusHostWindow(
    val window: NucleusWindow,
) : DockHostWindow {
    override fun contentBoundsOnScreen(): Rect? {
        if (!window.isPlaceable) return null
        val bounds = window.boundsOnScreen() ?: return null
        return Rect(bounds.x, bounds.y, bounds.x + bounds.width, bounds.y + bounds.height)
    }

    override val density: Float? get() = window.unsafe.taoWindow?.scaleFactor

    override val isFocused: Boolean get() = window.isFocused

    override fun moveContentTo(position: Offset) {
        window.unsafe.taoWindow?.setOuterPosition(position.x.toDouble(), position.y.toDouble())
    }
}

/**
 * Whether this window's screen position can be read and set. False on native Wayland, where the
 * compositor alone places windows and reports every one at (0, 0).
 */
private val NucleusWindow.isPlaceable: Boolean
    get() = unsafe.taoWindow?.canPlaceOnScreen ?: true

/** The Nucleus window showing [windowId]'s dock area. */
private fun DockState.nucleusWindow(windowId: WindowId = WindowId.MAIN): NucleusWindow? =
    (hostWindow(windowId) as? NucleusHostWindow)?.window

/**
 * A [DockArea] in the Nucleus window it is composed in, connected to the game's other windows.
 *
 * Where windows can be placed (X11, Windows, macOS) drags run in screen coordinates. On native
 * Wayland, where no window can see the pointer outside itself, a drag is carried by the platform's
 * drag-and-drop session instead: the grip starts it, the window under the pointer reports where it
 * is in its own coordinates, and a release over no window tears the panel off into a detached
 * window (placed by the compositor).
 */
@Composable
internal fun GameDockArea(
    state: DockState,
    modifier: Modifier = Modifier,
    windowId: WindowId = WindowId.MAIN,
) {
    val window = LocalNucleusWindow.current
    val host = remember(window) { NucleusHostWindow(window) }
    registerDockingWindow(state, windowId, host)
    if (window.isPlaceable) {
        DockArea(state, windowId, modifier)
        return
    }
    // A window that cannot be placed cannot be dragged by a panel header either: there the header
    // drags its panel through the platform session, which is what lets it be dropped back into
    // another window.
    val handover = remember(state, window) { TransferHandover(state, window) }
    CompositionLocalProvider(LocalDockingDragHandover provides handover) {
        DockArea(state, windowId, modifier.transferDropTarget(state, windowId))
    }
}

/**
 * Moves the detached window [windowId] by dragging this element. Where windows can be placed, the
 * docking system drives the move, so letting go over a dock area docks the window there. On native
 * Wayland only the compositor can move a window, so the drag is handed to it and docking back is
 * left to the title bar's dock button and the panel's own header.
 */
@Composable
internal fun Modifier.detachedWindowDragArea(
    state: DockState,
    windowId: WindowId,
): Modifier {
    val window = LocalNucleusWindow.current
    if (window.isPlaceable) return dockWindowDragArea(state, windowId)
    val tao = window.unsafe.taoWindow ?: return this
    return pointerInput(tao) {
        awaitEachGesture {
            awaitFirstDown()
            // As Nucleus's own title bar does: the compositor's move starts with the first movement
            // of the press, so a click on the bar stays a click.
            val change = awaitPointerEvent().changes.firstOrNull()
            if (change?.pressed == true) tao.dragWindow()
        }
    }
}

/** Hands every grip's drag to the platform's drag-and-drop session (Nucleus's transfer drag). */
private class TransferHandover(
    private val state: DockState,
    private val window: NucleusWindow,
) : DockingDragHandover {
    @Composable
    override fun gripModifier(grip: DockDragGrip): Modifier {
        val tao = window.unsafe.taoWindow ?: return Modifier
        val gesture = remember(grip) { GripGesture(grip) }
        return Modifier.transferDragHandle(
            key = grip.key,
            window = tao,
            begin = {
                // The platform session starts here, from the press; the docking drag starts with
                // it, at the last position the gesture saw.
                if (grip.begin(gesture.lastPosition ?: Offset.Zero)) DockTransferDrag(state, grip) else null
            },
            gesture = gesture,
        )
    }
}

/**
 * What the grip does before the platform session takes the gesture: a tab reorders within its
 * strip, everything else hands over as soon as the touch slop is passed.
 */
private class GripGesture(
    private val grip: DockDragGrip,
) : TransferDragGesture {
    var lastPosition: Offset? = null
        private set

    override fun onStart(pressPosition: Offset) {
        lastPosition = pressPosition
    }

    override fun onDrag(position: Offset): Boolean {
        lastPosition = position
        return !grip.stayLocal(position)
    }

    override fun onEnd(released: Boolean) {
        grip.endLocal(released, lastPosition)
    }
}

/** The drag icon's title card: a header-sized strip, whatever the size of what is dragged. */
private val GhostCardSizePx = Size(240f, 48f)

/** The platform session of one docking drag, as Nucleus's transfer drag carries it. */
private class DockTransferDrag(
    private val state: DockState,
    grip: DockDragGrip,
) : TransferDrag {
    private val bounds = grip.sourceBoundsInWindow

    override val title: String = grip.title

    // Only the card: a picture of the source comes from ghostSource, which Nucleus reduces itself.
    // A card the size of a whole game window would hide the zones it is aimed at.
    override val ghostSizePx: Size =
        bounds?.size?.takeIf { it.width > 0f && it.height > 0f }?.let {
            Size(minOf(it.width, GhostCardSizePx.width), minOf(it.height, GhostCardSizePx.height))
        } ?: GhostCardSizePx

    override val ghostSource: TransferGhostSource =
        bounds?.let {
            TransferGhostSource.Region(
                IntRect(it.left.roundToInt(), it.top.roundToInt(), it.right.roundToInt(), it.bottom.roundToInt()),
            )
        } ?: TransferGhostSource.None

    override fun end() {
        state.windowHost.endPlatformDrag()
    }

    override fun cancel() {
        state.windowHost.cancel()
    }
}

/**
 * This window as a drop target of the docking drags the platform carries: it reports the pointer in
 * its own coordinates, which is all a window on native Wayland can know of it.
 */
@Composable
private fun Modifier.transferDropTarget(
    state: DockState,
    windowId: WindowId,
): Modifier {
    val target =
        remember(state, windowId) {
            val host = state.windowHost
            object : DragAndDropTarget {
                override fun onEntered(event: DragAndDropEvent) = host.hoverInWindow(event.positionInWindowPx(), windowId)

                override fun onMoved(event: DragAndDropEvent) = host.hoverInWindow(event.positionInWindowPx(), windowId)

                override fun onExited(event: DragAndDropEvent) = host.exitWindow(windowId)

                override fun onDrop(event: DragAndDropEvent): Boolean = host.dropInWindow(event.positionInWindowPx(), windowId)
            }
        }
    return dragAndDropTarget(
        // Only a docking drag in flight: its token is the same as every other transfer drag's.
        shouldStartDragAndDrop = { state.windowHost.isDragging },
        target = target,
    )
}

/** Size of the window following the pointer during a drag; the renderer's card sits inside it. */
private val DragPreviewSize = DpSize(240.dp, 48.dp)

/**
 * The small undecorated always-on-top window following the pointer while a game window is dragged.
 * Not drawn where windows cannot be placed: the compositor's drag icon is the preview there.
 */
@Composable
internal fun NucleusApplicationScope.GameDragPreviewWindow(state: DockState) {
    val host = state.windowHost
    // Both ways the preview is the application's: this window where windows can be placed, the
    // compositor's drag icon (the platform session's) where they cannot.
    DisposableEffect(host) {
        val claim = host.claimDragPreview()
        onDispose { claim.release() }
    }
    val preview = host.dragPreview ?: return
    // Asked once a drag runs, when the main window is registered.
    if (state.nucleusWindow()?.isPlaceable != true) return
    val windowState =
        rememberWindowState(
            position = previewPosition(preview.screenPosition),
            size = DragPreviewSize,
        )
    LaunchedEffect(host, windowState) {
        snapshotFlow { host.dragPreview?.screenPosition }.collect { position ->
            if (position != null) windowState.position = previewPosition(position)
        }
    }
    DecoratedWindow(
        onCloseRequest = {},
        state = windowState,
        undecorated = true,
        resizable = false,
        focusable = false,
        alwaysOnTop = true,
        hiddenFromDock = true,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
            val spec = state.registry[preview.dockableId]
            LocalDockingRenderer.current.DragPreview(
                title = spec?.title?.invoke() ?: preview.dockableId.value,
                icon = spec?.icon?.invoke(),
            )
        }
    }
}

private fun previewPosition(screen: Offset): WindowPosition.Absolute = WindowPosition.Absolute((screen.x + 14).dp, (screen.y + 14).dp)
