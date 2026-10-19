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
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The crash behind DESKTOP-3T and DESKTOP-40: a press in a row that wraps, dragged down into the
 * start padding of a later row, left Compose 1.12.0 with a backwards selection inside the pressed
 * row whose handles were marked uncrossed. The next frame threw `Start(n) or End(m) is out of
 * range ... or start > end!` drawing it, from the render loop, which the uncaught handler turns into
 * a dead client. Compose 1.12.1 reworked how a handle between two texts resolves and no longer
 * produces it.
 *
 * Nothing in the window code can work around it - the bad range lives in Compose's selection
 * manager - so this pins the Compose behavior: the gesture is the one a fuzzer found against 1.12.0,
 * in a list shaped like the window's (wrapping rows, start padding, vertical list padding).
 */
class SelectionDrawRangeTest {
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun aDragIntoALaterRowsPaddingDrawsWithoutThrowing() {
        val texts =
            listOf(
                "ab",
                "row one " + "word ".repeat(30),
                "x",
                "row three " + "word ".repeat(12),
                "",
                "row five word word",
            )
        val state = SelectionState()
        val scene = ImageComposeScene(width = 300, height = 200, density = Density(1f))
        try {
            scene.setContent {
                SelectionContainer(state = state) {
                    LazyColumn(Modifier.fillMaxSize().padding(vertical = 4.dp)) {
                        items(texts.size) { index ->
                            Box(Modifier.fillMaxWidth().padding(start = 8.dp, end = 4.dp)) {
                                BasicText(texts[index])
                            }
                        }
                    }
                }
            }
            scene.render()
            val pressed = PointerButtons(isPrimaryPressed = true)
            // Inside the wrapped "row one", then left of the text of a row further down.
            scene.sendPointerEvent(PointerEventType.Press, Offset(276.9f, 15.1f), buttons = pressed)
            scene.render()
            scene.sendPointerEvent(PointerEventType.Move, Offset(7.6f, 107.2f), buttons = pressed)
            scene.render()
            scene.render()
            assertTrue(state.selectedTexts.isNotEmpty(), "the drag should have selected text")
        } finally {
            scene.close()
        }
    }
}
