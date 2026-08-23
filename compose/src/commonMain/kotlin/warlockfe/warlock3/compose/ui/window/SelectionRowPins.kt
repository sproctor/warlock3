package warlockfe.warlock3.compose.ui.window

import androidx.compose.foundation.text.selection.SelectionState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LocalPinnableContainer
import androidx.compose.ui.layout.PinnableContainer

/**
 * Keeps the rows a selection can be anchored in composed for as long as the selection lives, and
 * drops the selection when one of them is disposed regardless.
 *
 * Compose's SelectionManager remembers its anchors as selectable ids and, on the next drag event,
 * looks them up among the selectables registered at that moment; `getDirectionById` has no guard
 * for an id that is gone, so a disposed anchor row is a `NoSuchElementException` out of the
 * pointer-input coroutine, which the uncaught handler turns into a dead client. The manager pins
 * the lazy rows a selection covers so that scrolling cannot dispose them - but only once the
 * selection has text in them. A press alone leaves a collapsed selection, anchored in a row and
 * pinning nothing, and it stays that way until the first drag event that moves it. A scroll in
 * that gap disposes the row: the sticky scroll on arriving game text, or the wheel. The same
 * collapsed selection is what a later shift-click extends from, long after the press.
 *
 * So the window pins the composed rows itself - from the press on, and again whenever the
 * selection changes - and holds the pins until the selection is gone. A pinned row then leaves the
 * composition only for a structural reason: its line left the buffer, the list re-keyed, or the
 * row stopped rendering. Those are the cases where the selection has to go, and [PinnedRow] clears
 * it while the row's selectable is still registered, before any pointer event can look it up.
 *
 * Wire it up with [rememberSelectionRowPins], [pinSelectionRows] on the `SelectionContainer`, and
 * [PinnedRow] in every lazy row that holds selectable text.
 */
internal class SelectionRowPins(
    private val state: SelectionState,
) {
    private val composed = mutableMapOf<Long, PinnableContainer?>()
    private val pinned = mutableMapOf<Long, PinnableContainer.PinnedHandle?>()

    /**
     * Whether the container has focus. A click alone leaves a collapsed selection - the anchor of a
     * later shift-click - with no selected text to show for itself, so focus is what says it is
     * still wanted; Compose drops it the moment focus leaves anyway.
     */
    var containerFocused by mutableStateOf(false)

    /** Rows currently pinned, by serial number. */
    val pinnedSerials: Set<Long>
        get() = pinned.keys

    /**
     * A row entered the composition. [container] is the lazy item's pinnable container, or null
     * outside a lazy layout, where there is nothing to pin and a row leaves only structurally.
     */
    fun rowComposed(
        serial: Long,
        container: PinnableContainer?,
    ) {
        composed[serial] = container
    }

    /**
     * A row left the composition. Returns true if it was pinned, in which case the selection may be
     * anchored in it and must be dropped.
     */
    fun rowDisposed(serial: Long): Boolean {
        composed.remove(serial)
        if (serial !in pinned) return false
        pinned.remove(serial)?.release()
        return true
    }

    /** Pins every composed row that is not pinned already. */
    fun pinComposedRows() {
        composed.forEach { (serial, container) ->
            if (serial !in pinned) {
                pinned[serial] = container?.pin()
            }
        }
    }

    fun releaseAll() {
        pinned.values.forEach { it?.release() }
        pinned.clear()
    }

    fun clearSelection() {
        state.clear()
    }

    /** Select every composed row, pinned first: the anchors land in rows composed right now. */
    fun selectAll() {
        pinComposedRows()
        state.selectAll()
    }

    /**
     * Follows the selection for as long as the composition lives: pins the composed rows on every
     * change, and once the container has neither focus nor selected text releases them and clears
     * whatever selection is left.
     */
    suspend fun follow() {
        snapshotFlow {
            state.selectedTexts.takeIf { it.isNotEmpty() || containerFocused }
        }.collect { live ->
            if (live != null) {
                pinComposedRows()
            } else {
                releaseAll()
                clearSelection()
            }
        }
    }
}

/** The pins for [state]'s selection, following it while this is in the composition. */
@Composable
internal fun rememberSelectionRowPins(state: SelectionState): SelectionRowPins {
    val rowPins = remember(state) { SelectionRowPins(state) }
    LaunchedEffect(rowPins) { rowPins.follow() }
    DisposableEffect(rowPins) {
        onDispose { rowPins.releaseAll() }
    }
    return rowPins
}

/**
 * For the `SelectionContainer`: pins the composed rows on every press, before the selection
 * anchors in one of them, and tracks the container's focus. Initial pass and never consumed, so
 * selection and links still see the press.
 */
internal fun Modifier.pinSelectionRows(rowPins: SelectionRowPins): Modifier =
    onFocusChanged { rowPins.containerFocused = it.hasFocus }
        .pointerInput(rowPins) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.type == PointerEventType.Press) {
                        rowPins.pinComposedRows()
                    }
                }
            }
        }

/**
 * Registers the lazy row for [serial] for as long as it is composed, and clears the selection if
 * the row is disposed while pinned. Call it from the row, inside the lazy item.
 */
@Composable
internal fun SelectionRowPins.PinnedRow(serial: Long) {
    val container = LocalPinnableContainer.current
    DisposableEffect(this, serial, container) {
        rowComposed(serial, container)
        onDispose {
            if (rowDisposed(serial)) {
                clearSelection()
            }
        }
    }
}
