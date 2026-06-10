package warlockfe.warlock3.compose.components

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.zIndex

/**
 * Lightweight drag-to-reorder helper for short, non-lazy lists rendered inside a scrolling
 * [androidx.compose.foundation.layout.Column] (e.g. the highlight settings lists). It tracks a
 * local copy of the items so the UI reorders smoothly while a drag is in progress, then reports the
 * final move via [onMove] once the drag settles.
 *
 * Usage: render [ReorderableState.items] in order, apply [reorderableItem] to each row's outer
 * element, and apply [reorderableHandle] to whatever serves as the drag handle.
 */
@Composable
fun <T> rememberReorderableState(
    items: List<T>,
    key: (T) -> Any,
    onMove: (fromIndex: Int, toIndex: Int) -> Unit,
): ReorderableState<T> {
    val state = remember { ReorderableState<T>() }
    state.keyOf = rememberUpdatedState(key).value
    state.onMove = rememberUpdatedState(onMove).value
    // Re-sync the local list whenever the source changes and we're not mid-drag. We deliberately
    // do NOT re-sync on drag end alone: the persisted reorder arrives asynchronously, so resetting
    // to the (still stale) source list here would briefly revert the user's drop.
    LaunchedEffect(items) {
        if (state.draggingKey == null && state.localItems != items) {
            state.localItems.clear()
            state.localItems.addAll(items)
        }
    }
    return state
}

class ReorderableState<T> internal constructor() {
    internal val localItems = mutableStateListOf<T>()
    internal val heights = mutableStateMapOf<Any, Int>()
    internal var draggingKey by mutableStateOf<Any?>(null)
    internal var offsetY by mutableFloatStateOf(0f)
    internal var keyOf: (T) -> Any = { it as Any }
    internal var onMove: (Int, Int) -> Unit = { _, _ -> }

    /** The items in their current (possibly mid-drag) order. Render these instead of the source list. */
    val items: List<T> get() = localItems

    internal fun indexOfKey(itemKey: Any): Int = localItems.indexOfFirst { keyOf(it) == itemKey }

    // Swap the dragged item past any neighbors whose midpoint the accumulated offset has crossed,
    // adjusting the offset so the item stays visually under the pointer after each swap.
    internal fun settleSwaps(itemKey: Any) {
        var index = indexOfKey(itemKey)
        if (index < 0) return
        while (offsetY > 0 && index < localItems.lastIndex) {
            val nextHeight = heights[keyOf(localItems[index + 1])] ?: break
            if (offsetY <= nextHeight / 2f) break
            localItems.add(index + 1, localItems.removeAt(index))
            offsetY -= nextHeight
            index++
        }
        while (offsetY < 0 && index > 0) {
            val prevHeight = heights[keyOf(localItems[index - 1])] ?: break
            if (offsetY >= -prevHeight / 2f) break
            localItems.add(index - 1, localItems.removeAt(index))
            offsetY += prevHeight
            index--
        }
    }
}

/**
 * Modifier for an item's outer container. Measures the item's height (so swaps know the
 * thresholds), lifts the dragged item above its siblings, and offsets it to follow the pointer.
 */
fun <T> Modifier.reorderableItem(
    state: ReorderableState<T>,
    key: Any,
): Modifier =
    this
        .zIndex(if (state.draggingKey == key) 1f else 0f)
        .graphicsLayer { translationY = if (state.draggingKey == key) state.offsetY else 0f }
        .onGloballyPositioned { state.heights[key] = it.size.height }

/** Modifier for the drag handle of the item identified by [key]. */
fun <T> Modifier.reorderableHandle(
    state: ReorderableState<T>,
    key: Any,
): Modifier =
    this.pointerInput(key) {
        var startIndex = -1
        detectDragGestures(
            onDragStart = {
                startIndex = state.indexOfKey(key)
                state.offsetY = 0f
                state.draggingKey = key
            },
            onDrag = { change, dragAmount ->
                change.consume()
                state.offsetY += dragAmount.y
                state.settleSwaps(key)
            },
            onDragEnd = {
                val endIndex = state.indexOfKey(key)
                state.draggingKey = null
                state.offsetY = 0f
                if (startIndex in state.localItems.indices && endIndex >= 0 && startIndex != endIndex) {
                    state.onMove(startIndex, endIndex)
                }
            },
            onDragCancel = {
                state.draggingKey = null
                state.offsetY = 0f
            },
        )
    }
