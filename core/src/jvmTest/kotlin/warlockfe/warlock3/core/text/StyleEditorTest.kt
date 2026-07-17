package warlockfe.warlock3.core.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StyleEditorTest {
    private val red = WarlockColor(red = 255, green = 0, blue = 0)
    private val blue = WarlockColor(red = 0, green = 0, blue = 255)

    @Test
    fun setTextColorWritesTheColor() {
        assertEquals(red, StyleLayer().applyEdit(StyleEdit.SetTextColor(red)).textColor)
    }

    @Test
    fun setUnspecifiedTextColorClearsIt() {
        val layer = StyleLayer(textColor = red)
        assertNull(layer.applyEdit(StyleEdit.SetTextColor(WarlockColor.Unspecified)).textColor)
    }

    @Test
    fun setBackgroundSupportsAllThreeStates() {
        assertEquals(Background.Fill(blue), StyleLayer().applyEdit(StyleEdit.SetBackground(Background.Fill(blue))).background)
        assertEquals(Background.None, StyleLayer().applyEdit(StyleEdit.SetBackground(Background.None)).background)
        assertEquals(
            Background.Unset,
            StyleLayer(background = Background.Fill(blue)).applyEdit(StyleEdit.SetBackground(Background.Unset)).background,
        )
    }

    @Test
    fun setItalicFalseOverridesRatherThanClears() {
        // Explicit false differs from unset: it lets a character layer turn off an inherited italic.
        val layer = StyleLayer().applyEdit(StyleEdit.SetItalic(false))
        assertEquals(false, layer.italic)
    }

    @Test
    fun resetClearsOnlyTheNamedAttribute() {
        val layer = StyleLayer(textColor = red, weight = 700, italic = true)
        val reset = layer.applyEdit(StyleEdit.Reset(StyleAttribute.Weight))
        assertNull(reset.weight)
        assertEquals(red, reset.textColor) // untouched
        assertEquals(true, reset.italic) // untouched
    }

    @Test
    fun resetBackgroundReturnsToUnset() {
        val layer = StyleLayer(background = Background.None)
        assertEquals(Background.Unset, layer.applyEdit(StyleEdit.Reset(StyleAttribute.Background)).background)
    }

    @Test
    fun sourceOfReportsTheSettingScope() {
        val stack =
            listOf(
                StyleScope.CHARACTER to StyleLayer(textColor = red),
                StyleScope.GLOBAL to StyleLayer(weight = 700),
                StyleScope.SKIN to StyleLayer(italic = true),
            )
        val sourced = resolveSourced(stack)
        assertEquals(StyleScope.CHARACTER, sourced.sourceOf(StyleAttribute.TextColor))
        assertEquals(StyleScope.GLOBAL, sourced.sourceOf(StyleAttribute.Weight))
        assertEquals(StyleScope.SKIN, sourced.sourceOf(StyleAttribute.Italic))
        assertNull(sourced.sourceOf(StyleAttribute.Underline))
    }

    @Test
    fun styleEditorModelForCharacterEditsTheCharacterLayer() {
        val model =
            styleEditorModel(
                characterLayer = StyleLayer(textColor = red),
                globalLayer = StyleLayer(weight = 700),
                skinLayer = StyleLayer(italic = true),
            )
        assertEquals(StyleScope.CHARACTER, model.editScope)
        assertEquals(StyleLayer(textColor = red), model.editLayer)
        assertEquals(StyleScope.CHARACTER, model.sourced.sourceOf(StyleAttribute.TextColor))
        assertEquals(StyleScope.GLOBAL, model.sourced.sourceOf(StyleAttribute.Weight))
        assertEquals(StyleScope.SKIN, model.sourced.sourceOf(StyleAttribute.Italic))
    }

    @Test
    fun styleEditorModelForGlobalExcludesCharacterLayer() {
        // Editing the global scope must not surface a character's overrides as "inherited".
        val model =
            styleEditorModel(
                characterLayer = null,
                globalLayer = StyleLayer(textColor = blue),
                skinLayer = StyleLayer(weight = 400),
            )
        assertEquals(StyleScope.GLOBAL, model.editScope)
        assertEquals(StyleLayer(textColor = blue), model.editLayer)
        assertEquals(StyleScope.GLOBAL, model.sourced.sourceOf(StyleAttribute.TextColor))
        assertEquals(StyleScope.SKIN, model.sourced.sourceOf(StyleAttribute.Weight))
    }

    @Test
    fun sampleStyleResolvesTheWholeStack() {
        val stack =
            listOf(
                StyleScope.CHARACTER to StyleLayer(textColor = red),
                StyleScope.GLOBAL to StyleLayer(weight = 700),
                StyleScope.SKIN to StyleLayer(background = Background.Fill(blue)),
            )
        val sample = sampleStyle(stack)
        assertEquals(red, sample.textColor)
        assertEquals(700, sample.weight)
        assertEquals(Background.Fill(blue), sample.background)
    }
}
