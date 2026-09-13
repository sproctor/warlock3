import warlockfe.warlock3.core.text.Background
import warlockfe.warlock3.core.text.StyleLayer
import warlockfe.warlock3.core.text.WarlockColor
import warlockfe.warlock3.telnet.ansi.AnsiColor
import warlockfe.warlock3.telnet.ansi.AnsiPalette
import warlockfe.warlock3.telnet.ansi.AnsiStyle
import warlockfe.warlock3.telnet.ansi.toWarlockStyles
import kotlin.test.Test
import kotlin.test.assertEquals

class AnsiStylesTests {
    @Test
    fun defaultStyleHasNoStyles() {
        assertEquals(emptyList(), AnsiStyle.Default.toWarlockStyles())
    }

    @Test
    fun basicColorIsNamedAndCarriesItsColor() {
        val styles = AnsiStyle(foreground = AnsiColor.Indexed(1)).toWarlockStyles()
        assertEquals(1, styles.size)
        assertEquals("ansi.red", styles[0].name)
        assertEquals(StyleLayer(textColor = AnsiPalette.color(AnsiColor.Indexed(1))), styles[0].layer)
    }

    @Test
    fun boldOnBasicColorBrightensInsteadOfThickening() {
        val styles = AnsiStyle(foreground = AnsiColor.Indexed(4), bold = true).toWarlockStyles()
        assertEquals(listOf("ansi.brightBlue"), styles.map { it.name })
        assertEquals(AnsiPalette.color(AnsiColor.Indexed(12)), styles[0].layer?.textColor)
    }

    @Test
    fun boldAloneIsAWeight() {
        val styles = AnsiStyle(bold = true).toWarlockStyles()
        assertEquals(listOf("ansi.bold"), styles.map { it.name })
        assertEquals(700, styles[0].layer?.weight)
    }

    @Test
    fun boldOnExtendedColorKeepsColorAndAddsWeight() {
        val styles = AnsiStyle(foreground = AnsiColor.Indexed(208), bold = true).toWarlockStyles()
        assertEquals(listOf("ansi.indexed", "ansi.bold"), styles.map { it.name })
    }

    @Test
    fun backgroundIsAFill() {
        val styles = AnsiStyle(background = AnsiColor.Rgb(1, 2, 3)).toWarlockStyles()
        assertEquals(listOf("ansiBackground.rgb"), styles.map { it.name })
        assertEquals(Background.Fill(WarlockColor(1, 2, 3)), styles[0].layer?.background)
    }

    @Test
    fun inverseSwapsColors() {
        val styles = AnsiStyle(foreground = AnsiColor.Indexed(1), inverse = true).toWarlockStyles()
        assertEquals(listOf("ansi.black", "ansiBackground.red"), styles.map { it.name })
    }

    @Test
    fun italicAndUnderline() {
        val styles = AnsiStyle(italic = true, underline = true).toWarlockStyles()
        assertEquals(listOf("ansi.italic", "ansi.underline"), styles.map { it.name })
        assertEquals(true, styles[0].layer?.italic)
        assertEquals(true, styles[1].layer?.underline)
    }

    @Test
    fun paletteCubeAndGreys() {
        assertEquals(WarlockColor(0, 0, 0), AnsiPalette.color(AnsiColor.Indexed(16)))
        assertEquals(WarlockColor(255, 255, 255), AnsiPalette.color(AnsiColor.Indexed(231)))
        assertEquals(WarlockColor(255, 135, 0), AnsiPalette.color(AnsiColor.Indexed(208)))
        assertEquals(WarlockColor(8, 8, 8), AnsiPalette.color(AnsiColor.Indexed(232)))
        assertEquals(WarlockColor(238, 238, 238), AnsiPalette.color(AnsiColor.Indexed(255)))
    }
}
