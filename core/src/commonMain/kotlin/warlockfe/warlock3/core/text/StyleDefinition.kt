package warlockfe.warlock3.core.text

data class StyleDefinition(
    val textColor: WarlockColor = WarlockColor.Unspecified,
    val backgroundColor: WarlockColor = WarlockColor.Unspecified,
    val entireLine: Boolean = false,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val fontFamily: String? = null,
    val fontSize: Float? = null,
) {
    fun mergeWith(other: StyleDefinition): StyleDefinition {
        return StyleDefinition(
            textColor = if (textColor.isSpecified()) textColor else other.textColor,
            backgroundColor = if (backgroundColor.isSpecified()) backgroundColor else other.backgroundColor,
            entireLine = entireLine || other.entireLine,
            bold = bold || other.bold,
            italic = italic || other.italic,
            underline = underline || other.underline,
            fontFamily = fontFamily ?: other.fontFamily,
            fontSize = fontSize ?: other.fontSize,
        )
    }
}

fun flattenStyles(styles: List<StyleDefinition>): StyleDefinition? {
    return styles
        .reduceOrNull { acc, warlockStyle ->
            acc.mergeWith(warlockStyle)
        }
}