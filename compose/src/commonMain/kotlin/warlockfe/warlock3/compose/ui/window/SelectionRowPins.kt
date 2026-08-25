package warlockfe.warlock3.compose.ui.window

import androidx.compose.foundation.text.selection.SelectionState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.layout.LocalPinnableContainer
import androidx.compose.ui.layout.PinnableContainer
import warlockfe.warlock3.compose.util.onEachPointerEvent

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
 * pinning nothing, until the first drag event that moves it; and a selection started on a blank
 * line never pins that row at all, since its share of the selection is empty. A scroll in either
 * state disposes the anchor row: the sticky scroll on arriving game text, or the wheel. The same
 * collapsed selection is what a later shift-click extends from, long after the press.
 *
 * So the window pins the composed rows itself - on every press, and again whenever the selection
 * gains text - and a pinned row then leaves the composition only for a structural reason: its line
 * left the buffer, the list re-keyed, or the row stopped rendering. Those are the cases where the
 * selection has to go, and [PinnedRow] clears it while the row's selectable is still registered,
 * before any pointer event can look it up.
 *
 * The pins let go three ways, so they neither outlive the selection they guard nor smother the
 * list's laziness:
 * - A fresh mouse press that reaches the selection machinery replaces the selection, so
 *   [selectionReplaced] keeps only the rows in view - the only rows the new selection can be
 *   anchored in - and lets the rest go. Only for an unconsumed press: one the scrollbar or a
 *   shift-click extension took does not touch the pins its selection may still need.
 * - A release with nothing anchored - no focus, no selected text - lets everything go
 *   ([releaseUnlessAnchored]); this is what unwinds a scrollbar drag in an unfocused window.
 * - Once the container has neither focus nor selected text, [follow] lets everything go and clears
 *   whatever selection is left. Focus matters because a click alone has no text to show for itself
 *   and is still the anchor of a later shift-click; Compose drops the selection when focus leaves
 *   anyway.
 *
 * Not snapshot state: nothing composes from it, and it is read and written from effects and
 * pointer handlers on the composition's thread.
 *
 * Wire it up with [rememberSelectionRowPins], [pinSelectionRows] on the `SelectionContainer`, and
 * [PinnedRow] in every lazy row that holds selectable text.
 */
internal class SelectionRowPins(
    private val state: SelectionState,
    private val visibleRows: () -> Collection<Long>,
) {
    private val composed = mutableMapOf<Long, PinnableContainer?>()
    private val pinned = mutableMapOf<Long, PinnableContainer.PinnedHandle?>()

    /**
     * Rows released by [selectionReplaced] while still composed. A pinned row stays composed, and
     * pinning "everything composed" would resurrect it before the next measure gets to dispose it -
     * the [follow] collector or a drag's first move can land in that gap - so these are off limits
     * to [pinComposedRows] until they are disposed or freshly composed.
     */
    private val retired = mutableSetOf<Long>()

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
        retired.remove(serial)
        composed[serial] = container
    }

    /**
     * A row left the composition. Returns true if it was pinned, in which case the selection may be
     * anchored in it and must be dropped.
     */
    fun rowDisposed(serial: Long): Boolean {
        retired.remove(serial)
        composed.remove(serial)
        if (serial !in pinned) return false
        pinned.remove(serial)?.release()
        return true
    }

    /** Pins every composed row that is not pinned already, except the [retired]. */
    fun pinComposedRows() {
        composed.forEach { (serial, container) ->
            if (serial !in pinned && serial !in retired) {
                pinned[serial] = container?.pin()
            }
        }
    }

    /**
     * A fresh gesture replaced the selection, so pins from before it guard anchors nothing
     * references any more: keep the rows in view - the only rows the new selection can be anchored
     * in - pin any of them that are not yet, and retire the rest. Without this, every press while
     * the window kept focus would add a viewport of rows for the buffer trim to eventually walk
     * through, each disposal wrongly clearing whatever selection is live by then.
     */
    fun selectionReplaced() {
        val visible = visibleRows().toSet()
        if (visible.isEmpty()) {
            // Nothing resolvable - the list has no measure to report yet. Guard whatever is
            // composed rather than guessing: a viewport over-held for one gesture is bounded,
            // a fresh anchor unpinned is the crash.
            pinComposedRows()
            return
        }
        pinned.keys.filter { it !in visible }.forEach { serial ->
            pinned.remove(serial)?.release()
            retired += serial
        }
        visible.forEach { serial ->
            // In view means resolvable, so a retirement no longer applies; without this a row
            // pinned here would still be blocked from re-pinning after the next release.
            retired.remove(serial)
            if (serial in composed && serial !in pinned) {
                pinned[serial] = composed.getValue(serial)?.pin()
            }
        }
    }

    /**
     * A gesture ended with nothing anchored anywhere - no focus to keep a collapsed click alive, no
     * selected text - so nothing can reference the rows, and the pins a consumed press took (the
     * scrollbar's, say) would otherwise have no other way out.
     */
    fun releaseUnlessAnchored() {
        if (!containerFocused && state.selectedTexts.isEmpty()) {
            releaseAll()
            clearSelection()
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
        // Unlike a gesture, select-all anchors in whatever is composed, in view or not.
        retired.clear()
        pinComposedRows()
        state.selectAll()
    }

    /**
     * Follows the selection for as long as the composition lives: pins the composed rows whenever
     * it has text - a handle drag can anchor it in a row composed since the press - and once the
     * container has neither focus nor selected text releases them and clears whatever selection is
     * left.
     */
    suspend fun follow() {
        snapshotFlow {
            state.selectedTexts.takeIf { it.isNotEmpty() || containerFocused }
        }.collect { live ->
            if (live == null) {
                releaseAll()
                clearSelection()
            } else if (live.isNotEmpty()) {
                pinComposedRows()
            }
        }
    }
}

/**
 * The pins for [state]'s selection, following it while this is in the composition. [visibleRows]
 * is the serials of the rows in the lazy list's viewport, read when a fresh press replaces the
 * selection; see [SelectionRowPins.selectionReplaced].
 */
@Composable
internal fun rememberSelectionRowPins(
    state: SelectionState,
    visibleRows: () -> Collection<Long>,
): SelectionRowPins {
    val currentVisibleRows = rememberUpdatedState(visibleRows)
    val rowPins = remember(state) { SelectionRowPins(state) { currentVisibleRows.value() } }
    LaunchedEffect(rowPins) { rowPins.follow() }
    DisposableEffect(rowPins) {
        onDispose { rowPins.releaseAll() }
    }
    return rowPins
}

/**
 * For the `SelectionContainer`: takes and releases pins as the gestures come in, and tracks the
 * container's focus. The Final pass, for what only it can see: whether the press was consumed - a
 * scrollbar drag or a shift-click extension must not be mistaken for a fresh selection - and with
 * the selection machinery already re-anchored by the time it runs, so what
 * [SelectionRowPins.selectionReplaced] keeps is exactly what the new selection can reach. Still
 * within the same event dispatch, ahead of any measure that could dispose a row. Consumes nothing.
 *
 * Only a mouse press replaces the pins: for a mouse, an unconsumed primary press always restarts
 * the selection, while on touch a press may just be the start of a scroll fling, so touch presses
 * only add.
 */
internal fun Modifier.pinSelectionRows(rowPins: SelectionRowPins): Modifier =
    onFocusChanged { rowPins.containerFocused = it.hasFocus }
        .onEachPointerEvent(pass = PointerEventPass.Final, key = rowPins) { event ->
            when (event.type) {
                PointerEventType.Press -> {
                    val freshMouseSelection =
                        event.changes.all { it.type == PointerType.Mouse && !it.isConsumed } &&
                            event.buttons.isPrimaryPressed &&
                            !event.keyboardModifiers.isShiftPressed
                    if (freshMouseSelection) {
                        rowPins.selectionReplaced()
                    } else {
                        rowPins.pinComposedRows()
                    }
                }

                PointerEventType.Release -> {
                    if (event.changes.none { it.pressed }) {
                        rowPins.releaseUnlessAnchored()
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
