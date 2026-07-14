package warlockfe.warlock3.core.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class ColorValueTest {
    private val red = WarlockColor(red = 255, green = 0, blue = 0)
    private val blue = WarlockColor(red = 0, green = 0, blue = 255)
    private val palette = mapOf("roomName" to red, "speech" to blue)

    @Test
    fun literalResolvesToItsColor() {
        assertEquals(red, ColorValue.Literal(red).resolve(palette))
    }

    @Test
    fun skinRefResolvesThroughThePalette() {
        assertEquals(blue, ColorValue.SkinRef("speech").resolve(palette))
    }

    @Test
    fun skinRefToMissingSlotIsUnspecified() {
        assertEquals(WarlockColor.Unspecified, ColorValue.SkinRef("nope").resolve(palette))
    }

    @Test
    fun resolveRefsLeavesALiteralLayerUntouched() {
        val layer = StyleLayer(textColor = red, background = Background.Fill(blue))
        assertSame(layer, layer.resolveRefs(palette))
    }

    @Test
    fun resolveRefsRefreshesTextColorFromTheSlotButKeepsTheRef() {
        val layer = StyleLayer(textColor = WarlockColor.Unspecified, textColorRef = "roomName")
        val resolved = layer.resolveRefs(palette)
        assertEquals(red, resolved.textColor)
        assertEquals("roomName", resolved.textColorRef) // still skin-tracked
    }

    @Test
    fun resolveRefsRefreshesTheBackgroundFill() {
        val layer = StyleLayer(background = Background.Fill(WarlockColor.Unspecified), backgroundRef = "speech")
        assertEquals(Background.Fill(blue), layer.resolveRefs(palette).background)
    }

    @Test
    fun aBackgroundRefResolvesEvenWhenTheCachedBackgroundIsUnset() {
        // A ref saved with its fill color dropped to unspecified (-> Unset on reload) must still resolve.
        val layer = StyleLayer(background = Background.Unset, backgroundRef = "roomName")
        assertEquals(Background.Fill(red), layer.resolveRefs(palette).background)
    }

    @Test
    fun settingABackgroundRefRecordsTheSlotAsAFill() {
        val layer = StyleLayer().applyEdit(StyleEdit.SetBackgroundRef("speech"))
        assertEquals("speech", layer.backgroundRef)
        assertEquals(Background.Fill(blue), layer.resolveRefs(palette).background)
    }

    @Test
    fun aLiteralBackgroundClearsTheBackgroundRef() {
        val layer = StyleLayer(backgroundRef = "speech").applyEdit(StyleEdit.SetBackground(Background.Fill(red)))
        assertEquals(null, layer.backgroundRef)
        assertEquals(Background.Fill(red), layer.background)
    }

    @Test
    fun resolveRefsKeepsLastColorWhenTheSlotIsGone() {
        val layer = StyleLayer(textColor = red, textColorRef = "removedSlot")
        assertEquals(red, layer.resolveRefs(palette).textColor)
    }

    @Test
    fun settingALiteralColorClearsTheRef() {
        val layer = StyleLayer(textColorRef = "roomName").applyEdit(StyleEdit.SetTextColor(blue))
        assertEquals(blue, layer.textColor)
        assertEquals(null, layer.textColorRef)
    }

    @Test
    fun settingARefRecordsTheSlot() {
        val layer = StyleLayer(textColor = red).applyEdit(StyleEdit.SetTextColorRef("speech"))
        assertEquals("speech", layer.textColorRef)
    }

    @Test
    fun resettingTextColorClearsTheRefToo() {
        val layer = StyleLayer(textColor = red, textColorRef = "roomName").applyEdit(StyleEdit.Reset(StyleAttribute.TextColor))
        assertEquals(null, layer.textColor)
        assertEquals(null, layer.textColorRef)
    }
}
