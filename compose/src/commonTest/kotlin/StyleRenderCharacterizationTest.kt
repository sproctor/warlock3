import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import warlockfe.warlock3.compose.util.toAnnotatedString
import warlockfe.warlock3.compose.util.toColor
import warlockfe.warlock3.compose.util.toSpanStyle
import warlockfe.warlock3.core.text.FontConfig
import warlockfe.warlock3.core.text.StyleDefinition
import warlockfe.warlock3.core.text.StyledString
import warlockfe.warlock3.core.text.WarlockColor
import warlockfe.warlock3.core.text.WarlockStyle
import warlockfe.warlock3.core.text.toLayer
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Golden characterization of how a [StyleDefinition] becomes a Compose [SpanStyle], and how a
 * [StyledString] with stacked named styles resolves to spans. This pins the CURRENT rendering behavior
 * so the appearance-model rewrite (weight-not-bold, tri-state background, per-item fonts, pure resolve)
 * can prove it reproduces the same output. If a change here is intentional, update the expectation
 * deliberately — do not "fix" the test to make it pass.
 */
class StyleRenderCharacterizationTest {
    private val red = WarlockColor(red = 255, green = 0, blue = 0)
    private val blue = WarlockColor(red = 0, green = 0, blue = 255)

    @Test
    fun emptyStyleIsAnEmptySpan() {
        assertEquals(SpanStyle(), StyleDefinition().toSpanStyle(null))
    }

    @Test
    fun boldMapsToFontWeightBold() {
        assertEquals(SpanStyle(fontWeight = FontWeight.Bold), StyleDefinition(bold = true).toSpanStyle(null))
    }

    @Test
    fun italicMapsToFontStyleItalic() {
        assertEquals(SpanStyle(fontStyle = FontStyle.Italic), StyleDefinition(italic = true).toSpanStyle(null))
    }

    @Test
    fun underlineMapsToTextDecoration() {
        assertEquals(SpanStyle(textDecoration = TextDecoration.Underline), StyleDefinition(underline = true).toSpanStyle(null))
    }

    @Test
    fun colorsMapThroughToColor() {
        assertEquals(
            SpanStyle(color = red.toColor(), background = blue.toColor()),
            StyleDefinition(textColor = red, backgroundColor = blue).toSpanStyle(null),
        )
    }

    @Test
    fun monospaceWithoutFontUsesGenericMonospace() {
        assertEquals(
            SpanStyle(fontFamily = FontFamily.Monospace, fontSize = TextUnit.Unspecified, fontWeight = null),
            StyleDefinition(monospace = true).toSpanStyle(null),
        )
    }

    @Test
    fun monospacePullsWeightAndSizeFromMonoFont() {
        assertEquals(
            SpanStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight(700), fontSize = 13f.sp),
            StyleDefinition(monospace = true).toSpanStyle(FontConfig(family = null, size = 13f, weight = 700)),
        )
    }

    @Test
    fun boldBeatsMonoFontWeight() {
        // The explicit bold flag wins over the monospace font's own weight.
        assertEquals(
            SpanStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 13f.sp),
            StyleDefinition(bold = true, monospace = true).toSpanStyle(FontConfig(family = null, size = 13f, weight = 400)),
        )
    }

    @Test
    fun plainTextHasNoSpans() {
        val annotated = StyledString("plain").toAnnotatedString(emptyMap(), emptyMap(), {}, null)
        assertEquals("plain", annotated.text)
        assertEquals(0, annotated.spanStyles.size)
    }

    @Test
    fun stackedNamedStylesFlattenEarliestColorAndOrBooleans() {
        // A leaf carrying [speech, bold]: color comes from the earliest specifying style (speech),
        // bold is ORed in. The rewrite's resolve() must preserve this exact outcome.
        val styleMap =
            mapOf(
                "speech" to StyleDefinition(textColor = red),
                "bold" to StyleDefinition(bold = true),
            ).mapValues { (_, style) -> style.toLayer() }
        val styled = StyledString("hi").applyStyle(WarlockStyle.Speech).applyStyle(WarlockStyle.Bold)
        val annotated = styled.toAnnotatedString(emptyMap(), styleMap, {}, null)

        assertEquals(1, annotated.spanStyles.size)
        val span = annotated.spanStyles.single()
        assertEquals(0, span.start)
        assertEquals(2, span.end)
        assertEquals(SpanStyle(color = red.toColor(), fontWeight = FontWeight.Bold), span.item)
    }

    @Test
    fun monospaceLeafSynthesizesAMonospaceSpan() {
        val annotated = StyledString("map").applyMonospace().toAnnotatedString(emptyMap(), emptyMap(), {}, null)
        assertEquals(
            FontFamily.Monospace,
            annotated.spanStyles
                .single()
                .item.fontFamily,
        )
    }
}
