package warlockfe.warlock3.telnet.ansi

import warlockfe.warlock3.core.text.Background
import warlockfe.warlock3.core.text.StyleLayer
import warlockfe.warlock3.core.text.WarlockColor
import warlockfe.warlock3.core.text.WarlockStyle

/**
 * The colours ANSI names and a MUD expects: the basic sixteen from a readable dark-background
 * palette (the one most terminals ship), the cube and grey ramp by the xterm formula.
 *
 * The sixteen are also reachable by name from a skin or the user's presets - `ansi.red`,
 * `ansiBackground.brightBlue` - since the [WarlockStyle] each becomes carries that name and the
 * renderer looks the name up before falling back to the colour here.
 */
object AnsiPalette {
    val names =
        listOf(
            "black",
            "red",
            "green",
            "yellow",
            "blue",
            "magenta",
            "cyan",
            "white",
            "brightBlack",
            "brightRed",
            "brightGreen",
            "brightYellow",
            "brightBlue",
            "brightMagenta",
            "brightCyan",
            "brightWhite",
        )

    private val basic =
        listOf(
            WarlockColor(0x00, 0x00, 0x00),
            WarlockColor(0xCD, 0x31, 0x31),
            WarlockColor(0x0D, 0xBC, 0x79),
            WarlockColor(0xE5, 0xE5, 0x10),
            WarlockColor(0x24, 0x72, 0xC8),
            WarlockColor(0xBC, 0x3F, 0xBC),
            WarlockColor(0x11, 0xA8, 0xCD),
            WarlockColor(0xE5, 0xE5, 0xE5),
            WarlockColor(0x66, 0x66, 0x66),
            WarlockColor(0xF1, 0x4C, 0x4C),
            WarlockColor(0x23, 0xD1, 0x8B),
            WarlockColor(0xF5, 0xF5, 0x43),
            WarlockColor(0x3B, 0x8E, 0xEA),
            WarlockColor(0xD6, 0x70, 0xD6),
            WarlockColor(0x29, 0xB8, 0xDB),
            WarlockColor(0xFF, 0xFF, 0xFF),
        )

    private val cubeLevels = intArrayOf(0, 95, 135, 175, 215, 255)

    fun color(color: AnsiColor): WarlockColor =
        when (color) {
            is AnsiColor.Rgb -> WarlockColor(color.red, color.green, color.blue)
            is AnsiColor.Indexed -> indexed(color.index)
        }

    private fun indexed(index: Int): WarlockColor =
        when (index) {
            in 0..15 -> {
                basic[index]
            }

            in 16..231 -> {
                val i = index - 16
                WarlockColor(cubeLevels[i / 36], cubeLevels[i / 6 % 6], cubeLevels[i % 6])
            }

            in 232..255 -> {
                val grey = 8 + (index - 232) * 10
                WarlockColor(grey, grey, grey)
            }

            else -> {
                basic[7]
            }
        }
}

/**
 * The [WarlockStyle]s that render this text the way a terminal would.
 *
 * Bold on one of the basic eight colours brightens the colour rather than thickening the text:
 * that is what every terminal MUDs were coloured on did, and what the MUD's colour scheme was
 * designed around. Bold on anything else is a heavier weight. Inverse swaps the two colours.
 * Faint and strikethrough have no rendering.
 */
fun AnsiStyle.toWarlockStyles(): List<WarlockStyle> {
    if (isDefault) return emptyList()
    var foreground = foreground
    var background = background
    if (bold && foreground is AnsiColor.Indexed && foreground.index < 8) {
        foreground = AnsiColor.Indexed(foreground.index + 8)
    }
    val weightApplies = bold && !(this.foreground is AnsiColor.Indexed && this.foreground.index < 8)
    if (inverse) {
        val swapped = background ?: AnsiColor.Indexed(0)
        background = foreground ?: AnsiColor.Indexed(7)
        foreground = swapped
    }
    return buildList {
        foreground?.let { add(foregroundStyle(it)) }
        background?.let { add(backgroundStyle(it)) }
        if (weightApplies) add(BOLD)
        if (italic) add(ITALIC)
        if (underline) add(UNDERLINE)
    }
}

private fun foregroundStyle(color: AnsiColor): WarlockStyle =
    WarlockStyle(
        name = styleName("ansi", color),
        layer = StyleLayer(textColor = AnsiPalette.color(color)),
    )

private fun backgroundStyle(color: AnsiColor): WarlockStyle =
    WarlockStyle(
        name = styleName("ansiBackground", color),
        layer = StyleLayer(background = Background.Fill(AnsiPalette.color(color))),
    )

// Only the basic sixteen have a name a skin could define; the rest share a name per kind so the
// lookup finds nothing and the inline colour applies.
private fun styleName(
    prefix: String,
    color: AnsiColor,
): String =
    when {
        color is AnsiColor.Indexed && color.index < 16 -> "$prefix.${AnsiPalette.names[color.index]}"
        color is AnsiColor.Indexed -> "$prefix.indexed"
        else -> "$prefix.rgb"
    }

private val BOLD = WarlockStyle(name = "ansi.bold", layer = StyleLayer(weight = 700))
private val ITALIC = WarlockStyle(name = "ansi.italic", layer = StyleLayer(italic = true))
private val UNDERLINE = WarlockStyle(name = "ansi.underline", layer = StyleLayer(underline = true))
