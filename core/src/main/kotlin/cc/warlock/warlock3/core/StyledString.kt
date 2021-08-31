package cc.warlock.warlock3.core

data class StyledString(val substrings: List<StyledStringLeaf>) {
    constructor(text: String, style: WarlockStyle? = null)
            : this(listOf(StyledStringLeaf(text, style)))

    fun append(string: StyledString): StyledString {
        return StyledString(substrings + string.substrings)
    }
}

data class StyledStringLeaf(val text: String, val style: WarlockStyle? = null)

class WarlockStyle(
        val textColor: WarlockColor = WarlockColor.default,
        val backgroundColor: WarlockColor = WarlockColor.default,
        val bold: Boolean = false,
        val italic: Boolean = false,
        val underline: Boolean = false,
        val monospace: Boolean = false
) {
    companion object {
        val monospaced: WarlockStyle = WarlockStyle(WarlockColor.default, WarlockColor.default,
                false, false, false, true)
    }
}

data class WarlockColor(val red: Int, val green: Int, val blue: Int) {
    companion object {
        val default = WarlockColor(-1, -1, -1)
    }
}