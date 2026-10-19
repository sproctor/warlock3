package warlockfe.warlock3.compose.ui.window

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.SelectionState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The crash behind DESKTOP-3T and DESKTOP-40: a press beside the end of a short row, dragged down
 * into the start padding of the next row - one that wraps - beside its last line, left Compose
 * 1.12.0 with a backwards selection inside the wrapped row whose handles were marked uncrossed. The next frame
 * threw `Start(n) or End(m) is out of range ... or start > end!` drawing it, from the render loop,
 * which the uncaught handler turns into a dead client. Compose 1.12.1 reworked how a handle between
 * two texts resolves and no longer produces it.
 *
 * Nothing in the window code can work around it - the bad range lives in Compose's selection
 * manager - so this pins the Compose behavior, in a list shaped like the window's (wrapping rows,
 * start padding, vertical list padding). The gesture is the one a fuzzer found against 1.12.0, aimed
 * from the measured rows rather than fixed pixels, and the geometry it depends on is checked first so
 * a font or layout change cannot quietly turn it into a gesture that never reaches the bug. The
 * failure itself is `render()` throwing.
 */
class SelectionDrawRangeTest {
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun aDragFromBesideAShortRowIntoAWrappedRowsPaddingDrawsWithoutThrowing() {
        val texts =
            listOf(
                "ab",
                "row one " + "word ".repeat(30),
                "x",
                "row three " + "word ".repeat(12),
                "",
                "row five word word",
            )
        val rowBounds = arrayOfNulls<Rect>(texts.size)
        val textBounds = arrayOfNulls<Rect>(texts.size)
        val layouts = arrayOfNulls<TextLayoutResult>(texts.size)
        val scene = ImageComposeScene(width = 300, height = 200, density = Density(1f))
        try {
            scene.setContent {
                SelectionContainer(state = SelectionState()) {
                    LazyColumn(Modifier.fillMaxSize().padding(vertical = 4.dp)) {
                        items(texts.size) { index ->
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .onGloballyPositioned { rowBounds[index] = it.boundsInRoot() }
                                    .padding(start = 8.dp, end = 4.dp),
                            ) {
                                BasicText(
                                    text = texts[index],
                                    modifier = Modifier.onGloballyPositioned { textBounds[index] = it.boundsInRoot() },
                                    onTextLayout = { layouts[index] = it },
                                )
                            }
                        }
                    }
                }
            }
            scene.render()

            val shortRow = checkNotNull(rowBounds[0])
            val shortText = checkNotNull(textBounds[0])
            val wrapped = checkNotNull(rowBounds[1])
            val wrappedText = checkNotNull(textBounds[1])
            val wrappedLayout = checkNotNull(layouts[1])
            val lastLine = wrappedLayout.lineCount - 1
            assertTrue(lastLine > 0, "row one should wrap")
            assertTrue(shortText.right < shortRow.right - 10f, "the short row should leave room beside its text")
            assertTrue(wrappedText.left - wrapped.left >= 4f, "row one should have start padding to drag into")
            val lastLineY =
                wrappedText.top + (wrappedLayout.getLineTop(lastLine) + wrappedLayout.getLineBottom(lastLine)) / 2f
            assertTrue(lastLineY < 200f, "row one's last line should be in view")

            // Beside the end of the short row, then into row one's start padding beside its last line.
            val press = Offset(shortRow.right - 10f, shortRow.center.y)
            val move = Offset(wrapped.left + 2f, lastLineY)
            val pressed = PointerButtons(isPrimaryPressed = true)
            scene.sendPointerEvent(PointerEventType.Press, press, buttons = pressed)
            scene.render()
            scene.sendPointerEvent(PointerEventType.Move, move, buttons = pressed)
            scene.render()
            scene.render()
        } finally {
            scene.close()
        }
    }
}
