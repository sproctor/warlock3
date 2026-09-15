package warlockfe.warlock3.telnet.ansi

/** A colour as an ANSI SGR sequence names it. */
sealed interface AnsiColor {
    /**
     * One of the 256 indexed colours: 0-7 the basic eight, 8-15 their bright forms, 16-231 the
     * 6x6x6 cube, 232-255 the grey ramp. The basic sixteen are the ones a palette (or a skin) gets
     * to decide; the rest are fixed by the xterm convention every MUD assumes.
     */
    data class Indexed(
        val index: Int,
    ) : AnsiColor

    /** A 24-bit colour, from `38;2;r;g;b`. */
    data class Rgb(
        val red: Int,
        val green: Int,
        val blue: Int,
    ) : AnsiColor
}

/** The attributes in force at a point in the text, as set by the SGR sequences seen so far. */
data class AnsiStyle(
    val foreground: AnsiColor? = null,
    val background: AnsiColor? = null,
    val bold: Boolean = false,
    val faint: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val inverse: Boolean = false,
    val strikethrough: Boolean = false,
) {
    val isDefault: Boolean
        get() = this == Default

    companion object {
        val Default = AnsiStyle()
    }
}

/** A run of text and the style it was sent in. */
data class AnsiSpan(
    val text: String,
    val style: AnsiStyle,
)
