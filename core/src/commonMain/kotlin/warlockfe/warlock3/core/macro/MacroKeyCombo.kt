package warlockfe.warlock3.core.macro

data class MacroKeyCombo(
    val keyCode: Long,
    val ctrl: Boolean = false,
    val alt: Boolean = false,
    val shift: Boolean = false,
    val meta: Boolean = false,
)
