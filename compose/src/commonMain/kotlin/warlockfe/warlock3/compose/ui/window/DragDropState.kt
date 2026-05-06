package warlockfe.warlock3.compose.ui.window

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import warlockfe.warlock3.core.window.WindowLocation

@Stable
class DragDropState {
    var draggedItem: WindowUiState? by mutableStateOf(null)
        private set
    var sourceLocation: WindowLocation? by mutableStateOf(null)
        private set
    var sourceIndex: Int by mutableIntStateOf(-1)
        private set

    var dragOffset: Offset by mutableStateOf(Offset.Zero)
        private set

    var dropTarget: DropTarget? by mutableStateOf(null)
        private set

    private val sectionBounds = mutableStateMapOf<WindowLocation, SectionInfo>()

    val isDragging: Boolean get() = draggedItem != null

    fun startDrag(
        item: WindowUiState,
        location: WindowLocation,
        index: Int,
        offset: Offset,
    ) {
        draggedItem = item
        sourceLocation = location
        sourceIndex = index
        dragOffset = offset
        dropTarget = null
    }

    fun updateDrag(offset: Offset) {
        dragOffset = offset
        dropTarget = computeDropTarget()
    }

    fun endDrag(): DropResult? {
        val item = draggedItem ?: return null
        val source = sourceLocation ?: return null
        val target = dropTarget

        val result =
            if (target != null && target.location != WindowLocation.MAIN) {
                DropResult(
                    name = item.name,
                    sourceLocation = source,
                    sourceIndex = sourceIndex,
                    target = target,
                )
            } else {
                null
            }

        clearState()
        return result
    }

    fun cancelDrag() {
        clearState()
    }

    fun registerSection(
        location: WindowLocation,
        bounds: Rect,
        itemBounds: List<Rect>,
        isVertical: Boolean,
    ) {
        sectionBounds[location] = SectionInfo(bounds, itemBounds, isVertical)
    }

    fun unregisterSection(location: WindowLocation) {
        sectionBounds.remove(location)
    }

    private fun computeDropTarget(): DropTarget? {
        val pointer = dragOffset

        for ((location, info) in sectionBounds) {
            if (location == WindowLocation.MAIN) continue
            if (!info.bounds.contains(pointer)) continue

            if (info.itemBounds.isEmpty()) {
                return DropTarget(location, 0)
            }

            val insertionIndex =
                if (info.isVertical) {
                    computeInsertionIndex(info.itemBounds) { it.center.y < pointer.y }
                } else {
                    computeInsertionIndex(info.itemBounds) { it.center.x < pointer.x }
                }

            return DropTarget(location, insertionIndex)
        }

        return null
    }

    private inline fun computeInsertionIndex(
        itemBounds: List<Rect>,
        crossinline isBeforePointer: (Rect) -> Boolean,
    ): Int {
        for (i in itemBounds.indices) {
            if (!isBeforePointer(itemBounds[i])) {
                return i
            }
        }
        return itemBounds.size
    }

    private fun clearState() {
        draggedItem = null
        sourceLocation = null
        sourceIndex = -1
        dragOffset = Offset.Zero
        dropTarget = null
    }
}

data class DropTarget(
    val location: WindowLocation,
    val insertionIndex: Int,
)

data class SectionInfo(
    val bounds: Rect,
    val itemBounds: List<Rect>,
    val isVertical: Boolean,
)

data class DropResult(
    val name: String,
    val sourceLocation: WindowLocation,
    val sourceIndex: Int,
    val target: DropTarget,
)
