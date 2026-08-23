package warlockfe.warlock3.compose.ui.window

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.SelectionState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.PinnableContainer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The crash these guard against (DESKTOP-3P): Compose's SelectionManager keeps its anchors as
 * selectable ids and looks them up on the next drag event with no guard for one that is gone, so a
 * lazy row that is disposed while a selection is anchored in it takes the client down. Compose pins
 * the rows a selection covers, but nothing between the press and the first drag event, when the
 * selection is collapsed - so a scroll in that gap, which the window's sticky auto-scroll supplies
 * whenever game text arrives, disposes the anchor row.
 *
 * Pinned against a real scene with the same building blocks the window view uses, because the
 * question is what Compose does with them. Without [pinSelectionRows] and [PinnedRow] the press,
 * scroll, move sequence in the first test throws `NoSuchElementException: Cannot find value for
 * key N` out of the pointer-input coroutine, which is the production stack trace.
 */
class SelectionRowPinsTest {
    @Test
    fun aPressedRowSurvivesAScrollSoTheDragCanContinue() =
        scene { s ->
            s.pointer(PointerEventType.Press, Offset(10f, 5f))
            s.scrollBy(1500f)

            s.pointer(PointerEventType.Move, Offset(10f, 60f))
            assertEquals(emptyList(), s.failures)
            assertTrue(0L in s.composed, "the pressed row should still be composed after the scroll")
            assertTrue(s.state.selectedTexts.isNotEmpty(), "the drag should have produced a selection")
        }

    @Test
    fun aClickedRowSurvivesAScrollSoAShiftClickCanExtendFromIt() =
        scene { s ->
            s.pointer(PointerEventType.Press, Offset(10f, 5f))
            s.pointer(PointerEventType.Release, Offset(10f, 5f), buttons = PointerButtons())
            s.scrollBy(1500f)

            s.pointer(
                PointerEventType.Press,
                Offset(10f, 60f),
                keyboardModifiers = PointerKeyboardModifiers(isShiftPressed = true),
            )
            assertEquals(emptyList(), s.failures)
            assertTrue(s.state.selectedTexts.isNotEmpty(), "the shift-click should have extended the selection")
        }

    /**
     * The other way an anchor goes unpinned: a selection with text in it, started on a blank line.
     * That row's share of the selection is empty, so Compose never pins it, even once the drag has
     * moved on. The press lands in the row's start padding, left of a text with no width, which
     * is what anchors there rather than in the next row.
     */
    @Test
    fun aBlankLineAnchorSurvivesTheScrollingItsOwnDragCauses() =
        scene { s ->
            s.pointer(PointerEventType.Press, Offset(1f, BLANK_ROW * ROW_HEIGHT + 10f))
            s.pointer(PointerEventType.Move, Offset(1f, 130f))
            assertEquals(listOf("", "line 4", "line 5"), s.state.selectedTexts.map { it.text })

            s.scrollBy(1500f)
            s.pointer(PointerEventType.Move, Offset(1f, 150f))
            assertEquals(emptyList(), s.failures)
            assertTrue(BLANK_ROW.toLong() in s.composed, "the blank anchor row should still be composed")
            assertTrue(s.state.selectedTexts.size > 3, "the drag should still be extending the selection")
        }

    @Test
    fun aTrimThatTakesAPinnedRowDropsTheSelectionBeforeTheNextDragEvent() =
        scene { s ->
            s.pointer(PointerEventType.Press, Offset(10f, 5f))
            s.pointer(PointerEventType.Move, Offset(10f, 40f))
            assertTrue(s.state.selectedTexts.isNotEmpty())

            // The trim, off the compose thread, as the stream's work queue does it.
            s.lines.value = s.lines.value.drop(5)
            s.render()
            assertFalse(0L in s.composed, "the trimmed rows should be gone")
            assertTrue(s.state.selectedTexts.isEmpty(), "the selection anchored in them should be gone with them")

            s.pointer(PointerEventType.Move, Offset(10f, 60f))
            assertEquals(emptyList(), s.failures)
        }

    /**
     * Pins from an earlier press must not outlive the selection they guarded: left in place, they
     * hold the window's oldest composed rows, and the buffer trim walking through them would clear
     * whatever selection is live by then - one anchored in rows that are all still composed.
     */
    @Test
    fun aNewSelectionLetsGoOfAnEarlierClicksPinsSoATrimCannotClearIt() =
        scene { s ->
            s.pointer(PointerEventType.Press, Offset(10f, 5f))
            s.pointer(PointerEventType.Release, Offset(10f, 5f), buttons = PointerButtons())
            s.scrollBy(1500f)
            assertTrue(0L in s.composed, "the click's rows are held while it could still be extended")

            // A fresh selection elsewhere: the click's pins are stale now, and its rows go.
            s.pointer(PointerEventType.Press, Offset(10f, 60f))
            s.pointer(PointerEventType.Move, Offset(10f, 100f))
            assertFalse(0L in s.composed, "the earlier click's rows should have been let go")
            val texts = s.selectedTexts()
            assertTrue(texts.isNotEmpty())

            // The trim walks through the old rows without touching the live selection.
            s.lines.value = s.lines.value.drop(20)
            s.render()
            assertEquals(texts, s.selectedTexts(), "the trim took nothing the selection is anchored in")
            assertEquals(emptyList(), s.failures)
        }

    /**
     * The stand-in scrollbar consumes its presses the way the real one consumes its drags. Such a
     * press is not a fresh selection: it must leave a live selection and the pins guarding it
     * alone. And when nothing is anchored anywhere, the pins it took itself go at its release -
     * without that, a scrollbar press in an unfocused window would pin a viewport of rows with no
     * release path at all.
     */
    @Test
    fun aConsumedPressLeavesTheSelectionAloneAndItsOwnPinsGoAtRelease() =
        scene { s ->
            // A selection, then a scrollbar drag: selection and pins both stay.
            s.pointer(PointerEventType.Press, Offset(10f, 5f))
            s.pointer(PointerEventType.Move, Offset(10f, 40f))
            s.pointer(PointerEventType.Release, Offset(10f, 40f), buttons = PointerButtons())
            val texts = s.selectedTexts()
            assertTrue(texts.isNotEmpty())
            s.pointer(PointerEventType.Press, Offset(290f, 60f))
            s.pointer(PointerEventType.Move, Offset(290f, 120f))
            s.pointer(PointerEventType.Release, Offset(290f, 120f), buttons = PointerButtons())
            assertEquals(texts, s.selectedTexts(), "a scrollbar drag should not disturb the selection")
            assertTrue(s.pinned.isNotEmpty(), "or the pins guarding it")

            // Nothing anchored: the press pins while it is down, the release lets go.
            s.call { s.otherFocus.requestFocus() }
            s.render()
            assertTrue(s.pinned.isEmpty())
            s.pointer(PointerEventType.Press, Offset(290f, 60f))
            assertTrue(s.pinned.isNotEmpty(), "a press guards whatever it might touch while it is down")
            s.pointer(PointerEventType.Release, Offset(290f, 60f), buttons = PointerButtons())
            assertTrue(s.pinned.isEmpty(), "nothing anchored, so the release lets the rows go")
            assertEquals(emptyList(), s.failures)
        }

    @Test
    fun thePinsAreReleasedOnceTheSelectionIsGone() =
        scene { s ->
            s.pointer(PointerEventType.Press, Offset(10f, 5f))
            val atPress = s.composed
            s.pointer(PointerEventType.Move, Offset(10f, 40f))
            s.scrollBy(1500f)
            assertTrue(s.composed.containsAll(atPress), "the rows composed at the press stay pinned")

            // Focus leaving the container is what drops the selection in Compose.
            s.call { s.otherFocus.requestFocus() }
            s.render()
            assertTrue(s.state.selectedTexts.isEmpty())
            assertTrue(s.composed.none { it in atPress }, "nothing should hold the rows once the selection is gone: ${s.composed}")
            assertEquals(emptyList(), s.failures)
        }

    @Test
    fun bookkeepingPinsEachRowOnceAndReleasesItOnce() {
        val pins = SelectionRowPins(SelectionState()) { emptySet() }
        val containers = (1L..3L).associateWith { FakeContainer() }
        containers.forEach { (serial, container) -> pins.rowComposed(serial, container) }

        pins.pinComposedRows()
        pins.pinComposedRows()
        assertEquals(setOf(1L, 2L, 3L), pins.pinnedSerials)
        assertTrue(containers.values.all { it.pins == 1 })

        // A row composed after the pins were taken is picked up by the next round.
        val late = FakeContainer()
        pins.rowComposed(4L, late)
        assertEquals(0, late.pins)
        pins.pinComposedRows()
        assertEquals(1, late.pins)

        // A pinned row leaving is reported, and its pin let go; an unpinned one is neither.
        assertTrue(pins.rowDisposed(2L))
        assertEquals(0, containers.getValue(2L).pins)
        pins.rowComposed(5L, FakeContainer())
        assertFalse(pins.rowDisposed(5L))

        pins.releaseAll()
        assertTrue(pins.pinnedSerials.isEmpty())
        assertTrue(containers.values.all { it.pins == 0 })
        assertEquals(0, late.pins)
        // And releasing everything twice releases nothing twice.
        pins.releaseAll()
        assertTrue(containers.values.all { it.pins == 0 })
    }

    @Test
    fun aReplacedSelectionKeepsTheVisibleRowsAndRetiresTheRest() {
        var visible = setOf<Long>()
        val pins = SelectionRowPins(SelectionState()) { visible }
        val containers = (1L..4L).associateWith { FakeContainer() }
        listOf(1L, 2L, 3L).forEach { pins.rowComposed(it, containers.getValue(it)) }
        pins.pinComposedRows()
        // Composed since the pins were taken, like a row the scroll just brought in.
        pins.rowComposed(4L, containers.getValue(4L))

        visible = setOf(2L, 4L)
        pins.selectionReplaced()
        assertEquals(setOf(2L, 4L), pins.pinnedSerials, "in view stays pinned or gets pinned; the rest goes")
        assertEquals(0, containers.getValue(1L).pins)
        assertEquals(1, containers.getValue(2L).pins)

        // The rows let go are still composed until the next measure, and must not be resurrected
        // by the broad pin a selection change runs - or the release would never stick.
        pins.pinComposedRows()
        assertEquals(setOf(2L, 4L), pins.pinnedSerials)
        // Their disposal is nobody's anchor leaving.
        assertFalse(pins.rowDisposed(3L))
        // But a row composed afresh is an ordinary row again.
        pins.rowComposed(1L, containers.getValue(1L))
        pins.pinComposedRows()
        assertTrue(1L in pins.pinnedSerials)
    }

    @Test
    fun nothingAnchoredMeansNothingHeld() {
        val pins = SelectionRowPins(SelectionState()) { emptySet() }
        val container = FakeContainer()
        pins.rowComposed(1L, container)
        pins.pinComposedRows()

        // Focused: a collapsed click's anchor may live here, so the rows are kept.
        pins.containerFocused = true
        pins.releaseUnlessAnchored()
        assertEquals(1, container.pins)

        pins.containerFocused = false
        pins.releaseUnlessAnchored()
        assertEquals(0, container.pins)
    }

    private class FakeContainer : PinnableContainer {
        var pins = 0

        override fun pin(): PinnableContainer.PinnedHandle {
            pins++
            return PinnableContainer.PinnedHandle {
                check(pins > 0) { "released more than pinned" }
                pins--
            }
        }
    }

    private class Scene(
        val composeThread: ExecutorService,
        val scene: ImageComposeScene,
        val state: SelectionState,
        val lines: MutableStateFlow<List<Long>>,
        val scrollState: LazyListState,
        val otherFocus: FocusRequester,
        val failures: MutableList<Throwable>,
        private val composedRows: MutableSet<Long>,
        private val rowPins: () -> SelectionRowPins,
    ) {
        val composed: Set<Long>
            get() = call { composedRows.toSet() }

        val pinned: Set<Long>
            get() = call { rowPins().pinnedSerials.toSet() }

        fun <T> call(block: () -> T): T = composeThread.submit(Callable(block)).get()

        fun selectedTexts(): List<String> = call { state.selectedTexts.map { it.text } }

        fun render() =
            call {
                scene.render()
                scene.render()
            }

        @OptIn(ExperimentalComposeUiApi::class)
        fun pointer(
            type: PointerEventType,
            position: Offset,
            buttons: PointerButtons = PointerButtons(isPrimaryPressed = true),
            keyboardModifiers: PointerKeyboardModifiers = PointerKeyboardModifiers(),
        ) {
            call {
                runCatching {
                    scene.sendPointerEvent(type, position, buttons = buttons, keyboardModifiers = keyboardModifiers)
                }.onFailure { failures += it }
            }
            render()
        }

        fun scrollBy(pixels: Float) {
            call { scrollState.dispatchRawDelta(pixels) }
            render()
        }
    }

    /**
     * 200 one-line rows in a 200px viewport, about a dozen of them composed, shaped like the
     * window's: fixed height, start padding, a blank line among the text, and a strip down the
     * right edge that consumes its presses, standing in for the desktop scrollbar. Confined to one
     * thread like the AWT event thread the app composes on, with a second focusable so focus can
     * leave the container.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    private fun scene(block: (Scene) -> Unit) {
        val composeThread = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "compose-test") }
        try {
            val failures = mutableListOf<Throwable>()
            val composedRows = mutableSetOf<Long>()
            val lines = MutableStateFlow((0L until 200L).toList())
            val scrollState = LazyListState()
            val state = SelectionState()
            val otherFocus = FocusRequester()
            val pinsHolder = arrayOfNulls<SelectionRowPins>(1)
            val dispatcher = composeThread.asCoroutineDispatcher()
            val scene =
                composeThread
                    .submit(
                        Callable {
                            // Where the app's crash surfaced, and where a coroutine's failure goes when
                            // nothing in its context takes it.
                            Thread.currentThread().setUncaughtExceptionHandler { _, t -> failures += t }
                            ImageComposeScene(
                                width = 300,
                                height = 200,
                                density = Density(1f),
                                coroutineContext = dispatcher + CoroutineExceptionHandler { _, t -> failures += t },
                            )
                        },
                    ).get()
            val s =
                Scene(composeThread, scene, state, lines, scrollState, otherFocus, failures, composedRows) {
                    checkNotNull(pinsHolder[0])
                }
            try {
                s.call {
                    scene.setContent {
                        val current by lines.collectAsState()
                        Column {
                            Box(Modifier.size(1.dp).focusRequester(otherFocus).focusable())
                            val rowPins =
                                rememberSelectionRowPins(state) {
                                    scrollState.layoutInfo.visibleItemsInfo.mapNotNull { it.key as? Long }
                                }
                            pinsHolder[0] = rowPins
                            SelectionContainer(state = state, modifier = Modifier.pinSelectionRows(rowPins)) {
                                Box {
                                    LazyColumn(Modifier.fillMaxWidth(), state = scrollState) {
                                        items(count = current.size, key = { index -> current[index] }) { index ->
                                            val serial = current[index]
                                            rowPins.PinnedRow(serial)
                                            DisposableEffect(serial) {
                                                composedRows += serial
                                                onDispose { composedRows -= serial }
                                            }
                                            Box(
                                                Modifier
                                                    .fillMaxWidth()
                                                    .height(ROW_HEIGHT.dp)
                                                    .padding(horizontal = 4.dp),
                                            ) {
                                                BasicText(text = if (serial == BLANK_ROW.toLong()) "" else "line $serial")
                                            }
                                        }
                                    }
                                    Box(
                                        Modifier
                                            .align(Alignment.TopEnd)
                                            .fillMaxHeight()
                                            .width(20.dp)
                                            .pointerInput(Unit) {
                                                awaitPointerEventScope {
                                                    while (true) {
                                                        val event = awaitPointerEvent()
                                                        if (event.type == PointerEventType.Press) {
                                                            event.changes.forEach { it.consume() }
                                                        }
                                                    }
                                                }
                                            },
                                    )
                                }
                            }
                        }
                    }
                }
                s.render()
                block(s)
            } finally {
                s.call { scene.close() }
            }
        } finally {
            composeThread.shutdown()
        }
    }
}

private const val BLANK_ROW = 3
private const val ROW_HEIGHT = 20f
