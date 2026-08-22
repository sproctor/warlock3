package warlockfe.warlock3.compose.util

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.text.TextRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EntryEditsTest {
    // {paste} used to replace the selection with TextFieldBuffer.replace, which leaves what it
    // wrote selected: pasting twice replaced the first paste with the second, so the entry never
    // collected more than one.
    @Test
    fun aSecondInsertFollowsTheFirstRatherThanReplacingIt() {
        val state = TextFieldState("hello")
        state.edit { selection = TextRange(0, 5) }

        state.insertReplacingSelection("one")
        assertEquals("one", state.text.toString())
        assertEquals(TextRange(3), state.selection, "the caret belongs after what went in")
        assertTrue(state.selection.collapsed, "and nothing is left selected")

        state.insertReplacingSelection("two")
        assertEquals("onetwo", state.text.toString())
        assertEquals(TextRange(6), state.selection)
        assertTrue(state.selection.collapsed)
    }

    @Test
    fun insertingWithoutASelectionLandsAtTheCaret() {
        val state = TextFieldState("hello world")
        state.edit { selection = TextRange(5) }

        state.insertReplacingSelection(",")

        assertEquals("hello, world", state.text.toString())
        assertEquals(TextRange(6), state.selection)
        assertTrue(state.selection.collapsed)
    }
}
