package warlockfe.warlock3.core.macro

data class MacroKeyCombo(
    val keyCode: Long,
    val ctrl: Boolean,
    val alt: Boolean,
    val shift: Boolean,
    val meta: Boolean,
) {
    fun encode(): String {
        val keyString = StringBuilder()
        if (ctrl) {
            keyString.append("ctrl+")
        }
        if (alt) {
            keyString.append("alt+")
        }
        if (shift) {
            keyString.append("shift+")
        }
        if (meta) {
            keyString.append("meta+")
        }
        keyString.append(keyCode.toString())
        return keyString.toString()
    }

    companion object {
        fun decode(text: String): MacroKeyCombo {
            val portions = text.split("+")
            return MacroKeyCombo(
                keyCode = portions.last().toLongOrNull() ?: 0L,
                ctrl = portions.contains("ctrl"),
                alt = portions.contains("alt"),
                shift = portions.contains("shift"),
                meta = portions.contains("meta"),
            )
        }
    }
}
