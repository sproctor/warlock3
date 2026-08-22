package warlockfe.warlock3.compose.util

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.delete
import androidx.compose.foundation.text.input.insert

/**
 * Puts [text] in at the caret, replacing the selection if there is one, and leaves the caret after
 * what went in.
 *
 * The caret is the point of doing it this way. `TextFieldBuffer.replace` leaves the text it wrote
 * *selected*, so a second paste lands on the first one and swaps it out instead of following it -
 * paste twice and you still have one copy.
 */
internal fun TextFieldState.insertReplacingSelection(text: String) {
    edit {
        if (selection.length > 0) {
            delete(selection.min, selection.max)
        }
        insert(selection.min, text)
    }
}
