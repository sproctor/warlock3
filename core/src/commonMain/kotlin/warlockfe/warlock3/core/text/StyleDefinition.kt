package warlockfe.warlock3.core.text

data class StyleDefinition(
    val textColor: WarlockColor = WarlockColor.Unspecified,
    val backgroundColor: WarlockColor = WarlockColor.Unspecified,
    val entireLine: Boolean = false,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val monospace: Boolean = false,
) {
    fun mergeWith(other: StyleDefinition): StyleDefinition =
        StyleDefinition(
            textColor = if (textColor.isSpecified()) textColor else other.textColor,
            backgroundColor = if (backgroundColor.isSpecified()) backgroundColor else other.backgroundColor,
            entireLine = entireLine || other.entireLine,
            bold = bold || other.bold,
            italic = italic || other.italic,
            underline = underline || other.underline,
            monospace = monospace || other.monospace,
        )
}

/*
 * Priority goes to earlier styles in the list
 */
fun flattenStyles(styles: List<StyleDefinition>): StyleDefinition? =
    styles
        .reduceOrNull { acc, warlockStyle ->
            acc.mergeWith(warlockStyle)
        }
