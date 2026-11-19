package warlockfe.warlock3.compose.components

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals

class PickerStartColorTest {
    @Test
    fun aColorStartsFromItself() {
        assertEquals(Color.Red, Color.Red.orWhite())
    }

    @Test
    fun nullStartsFromWhite() {
        assertEquals(Color.White, null.orWhite())
    }

    @Test
    fun unspecifiedStartsFromWhite() {
        assertEquals(Color.White, Color.Unspecified.orWhite())
    }
}
