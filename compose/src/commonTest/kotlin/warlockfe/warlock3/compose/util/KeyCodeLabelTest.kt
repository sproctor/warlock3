package warlockfe.warlock3.compose.util

import kotlin.test.Test
import kotlin.test.assertEquals

class KeyCodeLabelTest {
    @Test
    fun singleWordCodesAreTitleCased() {
        assertEquals("Escape", keyCodeLabel("ESCAPE"))
        assertEquals("Enter", keyCodeLabel("ENTER"))
        assertEquals("Space", keyCodeLabel("SPACE"))
    }

    @Test
    fun underscoresBecomeSpaces() {
        assertEquals("Page Up", keyCodeLabel("PAGE_UP"))
        assertEquals("Numpad 5", keyCodeLabel("NUMPAD_5"))
        assertEquals("Ctrl Left", keyCodeLabel("CTRL_LEFT"))
    }

    @Test
    fun lettersAndFunctionKeysAreLeftAlone() {
        assertEquals("A", keyCodeLabel("A"))
        assertEquals("Z", keyCodeLabel("Z"))
        assertEquals("F1", keyCodeLabel("F1"))
        assertEquals("F12", keyCodeLabel("F12"))
    }

    @Test
    fun digitsShowAsDigitsRatherThanTheirNames() {
        // The table names them ZERO..NINE, which is right for a stored key string and wrong on a
        // button.
        assertEquals("0", keyCodeLabel("ZERO"))
        assertEquals("7", keyCodeLabel("SEVEN"))
    }
}
