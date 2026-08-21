package warlockfe.warlock3.compose.ui.game

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import warlockfe.warlock3.compose.generated.resources.Res
import warlockfe.warlock3.compose.generated.resources.close
import warlockfe.warlock3.compose.generated.resources.more_vert
import warlockfe.warlock3.core.window.WindowInfo
import warlockfe.warlock3.core.window.WindowType

private val TabHeight = 48.dp
private val MenuButtonSize = 28.dp
private val CloseTargetSize = 64.dp

// A finger mid-drag, on a phone, over a target it cannot see under its own hand. Generous on
// purpose: the cost of overshooting is closing the wrong tab, which the user then has to re-add.
private val CloseTargetSlop = 24.dp

/**
 * The live state of a tab drag, shared by the strip that reports the pointer and the close target
 * that consumes it.
 *
 * The reorder library moves items *within* the strip and knows nothing about a target outside it,
 * and a horizontal drag only ever translates the tab along x - so the tab itself never travels
 * toward the target and its bounds cannot answer whether the drop landed there. The pointer has to
 * be tracked separately, which is what this holds.
 */
@Stable
class TabDragState {
    var draggingTab by mutableStateOf<String?>(null)
        private set

    private var pointer by mutableStateOf(Offset.Unspecified)

    /** The close target's bounds in root coordinates, already grown by its slop. */
    var closeTargetBounds by mutableStateOf(Rect.Zero)

    /**
     * Only ever read as a boolean. [pointer] changes every frame of a drag; deriving the answer
     * means the target recomposes when the finger crosses its edge rather than sixty times a second.
     */
    val isOverCloseTarget: Boolean by derivedStateOf {
        draggingTab != null && pointer.isSpecified && closeTargetBounds.contains(pointer)
    }

    fun onPointer(positionInRoot: Offset) {
        pointer = positionInRoot
    }

    fun onDragStarted(name: String) {
        draggingTab = name
        pointer = Offset.Unspecified
    }

    /** The tab to close, or null when this was an ordinary reorder. Ends the drag either way. */
    fun onDragStopped(): String? {
        val dropped = isOverCloseTarget
        val name = draggingTab
        draggingTab = null
        pointer = Offset.Unspecified
        closeTargetBounds = Rect.Zero
        return name?.takeIf { dropped }
    }
}

/**
 * The phone tab strip: the windows the user has picked, in the order they put them in.
 *
 * A tab is dragged by long press, because press-and-move has to stay free for scrolling the strip
 * when the tabs overflow. Dragging reorders live, so a tab dropped on the close target needs no
 * undo - removing an item leaves everything else in the order it already reached.
 */
@Composable
fun PhoneTabStrip(
    windows: List<WindowInfo>,
    tabs: List<String>,
    selected: String,
    drag: TabDragState,
    onSelect: (String) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    onClose: (String) -> Unit,
    onOpenWindowSettings: (String) -> Unit,
    onClearWindow: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val reorderState =
        rememberReorderableLazyListState(
            lazyListState = listState,
            scrollThresholdPadding = PaddingValues(horizontal = 8.dp),
        ) { from, to -> onMove(from.index, to.index) }
    val haptics = LocalHapticFeedback.current
    val byName = remember(windows) { windows.associateBy { it.name } }
    var stripCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }

    Box(
        modifier
            .onGloballyPositioned { stripCoords = it }
            // Reads the pointer without consuming, so the library's own drag detector is untouched.
            // On the Initial pass, which for this node runs before the tab's Main-pass handler, so
            // the release position is recorded before onDragStopped asks where the drop landed.
            .pointerInput(drag) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (drag.draggingTab == null) continue
                        val change = event.changes.firstOrNull() ?: continue
                        val coords = stripCoords?.takeIf { it.isAttached } ?: continue
                        drag.onPointer(coords.localToRoot(change.position))
                    }
                }
            },
    ) {
        LazyRow(
            state = listState,
            // No leading or trailing item may be added here: onMove reports a LazyListItemInfo
            // index, and it has to line up with the tab index.
            contentPadding = PaddingValues(horizontal = 8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(tabs.size, key = { tabs[it] }) { index ->
                val name = tabs[index]
                val info = byName[name]
                ReorderableItem(reorderState, key = name) { isDragging ->
                    PhoneTab(
                        title = info?.title ?: name,
                        isStream = info?.windowType == WindowType.STREAM,
                        selected = name == selected,
                        isDragging = isDragging,
                        canClose = name != MAIN_WINDOW_NAME,
                        onSelect = { onSelect(name) },
                        onOpenWindowSettings = { onOpenWindowSettings(name) },
                        onClearWindow = { onClearWindow(name) },
                        onClose = { onClose(name) },
                        onDragStart = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            drag.onDragStarted(name)
                        },
                        onDragStop = { drag.onDragStopped()?.let(onClose) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ReorderableCollectionItemScope.PhoneTab(
    title: String,
    isStream: Boolean,
    selected: Boolean,
    isDragging: Boolean,
    canClose: Boolean,
    onSelect: () -> Unit,
    onOpenWindowSettings: () -> Unit,
    onClearWindow: () -> Unit,
    onClose: () -> Unit,
    onDragStart: () -> Unit,
    onDragStop: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val contentColor =
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier =
            Modifier
                .height(TabHeight)
                .graphicsLayer { alpha = if (isDragging) 0.85f else 1f }
                // Tap selects, long press drags. The click has to come first: the drag detector
                // accepts an already-consumed down, so an inner handle still sees the press, while
                // an inner clickable would swallow the down before the long press could start.
                .clickable(onClick = onSelect)
                .longPressDraggableHandle(
                    onDragStarted = { onDragStart() },
                    onDragStopped = { onDragStop() },
                ).padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall,
            )
            if (selected) {
                Box {
                    // Not an IconButton: its minimum interactive size would burst the tab. The
                    // touch target is the tab itself, which selects - this only has to be hittable.
                    Box(
                        modifier =
                            Modifier
                                .size(MenuButtonSize)
                                .clip(CircleShape)
                                .clickable { menuOpen = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.more_vert),
                            contentDescription = "Window menu",
                            tint = contentColor,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Window settings ...") },
                            onClick = {
                                menuOpen = false
                                onOpenWindowSettings()
                            },
                        )
                        // A panel is a fixed layout of widgets with no text stream behind it, so
                        // there is nothing for "Clear window" to clear.
                        if (isStream) {
                            DropdownMenuItem(
                                text = { Text("Clear window") },
                                onClick = {
                                    menuOpen = false
                                    onClearWindow()
                                },
                            )
                        }
                        if (canClose) {
                            DropdownMenuItem(
                                text = { Text("Close") },
                                onClick = {
                                    menuOpen = false
                                    onClose()
                                },
                            )
                        }
                    }
                }
            } else {
                // Holds the menu button's slot open. Without it, selecting a tab widens it and
                // shoves every tab after it sideways on each tap.
                Spacer(Modifier.width(MenuButtonSize))
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent),
        )
    }
}

/**
 * The target a dragged tab is dropped on to take it out of the strip. Deliberately not clickable:
 * it is a place to let go of a tab, never a button, and a hit target of its own would fight the
 * drag it exists to end.
 */
@Composable
fun TabCloseTarget(
    active: Boolean,
    onBounds: (Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    val slop = with(LocalDensity.current) { CloseTargetSlop.toPx() }
    val scale by animateFloatAsState(if (active) 1.15f else 1f, label = "closeTargetScale")
    Box(
        modifier =
            modifier
                .size(CloseTargetSize)
                .scale(scale)
                .onGloballyPositioned { onBounds(it.boundsInRoot().inflate(slop)) }
                .clip(CircleShape)
                .background(
                    if (active) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    },
                ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(Res.drawable.close),
            contentDescription = "Drop here to remove this tab",
            tint =
                if (active) {
                    MaterialTheme.colorScheme.onError
                } else {
                    MaterialTheme.colorScheme.onErrorContainer
                },
        )
    }
}
