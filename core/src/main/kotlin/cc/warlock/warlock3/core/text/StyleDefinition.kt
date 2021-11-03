package cc.warlock.warlock3.core.text

data class StyleDefinition(
    val textColor: WarlockColor = WarlockColor(-1),
    val backgroundColor: WarlockColor = WarlockColor(-1),
    val entireLine: Boolean = false,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val monospace: Boolean = false
) {
    fun mergeWith(other: StyleDefinition): StyleDefinition {
        return StyleDefinition(
            textColor = if (textColor.isSpecified()) textColor else other.textColor,
            backgroundColor = if (backgroundColor.isSpecified()) backgroundColor else other.backgroundColor,
            entireLine = entireLine || other.entireLine,
            bold = bold || other.bold,
            italic = italic || other.italic,
            underline = underline || other.underline,
            monospace = monospace || other.monospace,
        )
    }
}

fun flattenStyles(styles: List<StyleDefinition>): StyleDefinition? {
    return styles
        .reduceOrNull { acc, warlockStyle ->
            acc.mergeWith(warlockStyle)
        }
}