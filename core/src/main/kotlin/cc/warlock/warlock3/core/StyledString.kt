package cc.warlock.warlock3.core

data class StyledString(val substrings: List<StyledStringLeaf>) {
    constructor(text: String, style: WarlockStyle? = null)
            : this(listOf(StyledStringLeaf(text, style)))

    fun append(string: StyledString): StyledString {
        return StyledString(substrings + string.substrings)
    }
}

data class StyledStringLeaf(val text: String, val style: WarlockStyle? = null)

data class WarlockStyle(
    val name: String? = null,
    val textColor: WarlockColor? = null,
    val backgroundColor: WarlockColor? = null,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val monospace: Boolean = false
) {
    fun mergeWith(other: WarlockStyle): WarlockStyle {
        return WarlockStyle(
            name = name ?: other.name,
            textColor = textColor ?: other.textColor,
            backgroundColor = backgroundColor ?: other.backgroundColor,
            bold = bold || other.bold,
            italic = italic || other.italic,
            underline = underline || other.underline,
            monospace = monospace || other.monospace,
        )
    }
}

data class WarlockColor(val red: Int, val green: Int, val blue: Int) {
//    companion object {
//        val default = WarlockColor(-1, -1, -1)
//    }
}