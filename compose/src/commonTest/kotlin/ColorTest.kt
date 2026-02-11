import androidx.compose.ui.graphics.Color
import warlockfe.warlock3.compose.util.parseHexOrNull
import warlockfe.warlock3.compose.util.toColor
import warlockfe.warlock3.compose.util.toWarlockColor
import warlockfe.warlock3.core.text.WarlockColor
import warlockfe.warlock3.core.text.toHexString
import kotlin.test.Test
import kotlin.test.assertEquals

class ColorTest {
    @Test
    fun color_without_alpha() {
        val color = Color.Red
        assertEquals(Color.Red, color.toWarlockColor().toColor())
    }

    @Test
    fun color_with_alpha() {
        val color = Color(0xFF00FF00)
        assertEquals(color, color.toWarlockColor().toColor())
    }

    @Test
    fun color_to_string() {
        val color = Color(0xFF00FF00)
        assertEquals("#ff00ff00", color.toWarlockColor().toHexString())
    }

    @Test
    fun string_to_color() {
        val colorString = "#ff00ff00"
        assertEquals(colorString, WarlockColor(colorString).toHexString())
        assertEquals(Color.Green, WarlockColor(colorString).toColor())
    }

    @Test
    fun parseHexToColor() {
        val colorString = "#ff00ff00"
        assertEquals(Color.Green, Color.parseHexOrNull(colorString))
    }
}