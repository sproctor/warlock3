package cc.warlock.warlock3.core

import java.util.*

class StyledString(text: String? = null, style: WarlockStyle? = null) {
    val substrings = LinkedList<StyledStringLeaf>()

    init {
        if (text != null)
            substrings.add(StyledStringLeaf(text, style))
    }

    fun append(string: StyledString) {
        substrings.addAll(string.substrings)
    }
}

class StyledStringLeaf(val text: String, val style: WarlockStyle? = null)

class WarlockStyle(
        var textColor: WarlockColor = WarlockColor.default,
        var backgroundColor: WarlockColor = WarlockColor.default,
        var bold: Boolean = false,
        var italic: Boolean = false,
        var underline: Boolean = false,
        var monospace: Boolean = false
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