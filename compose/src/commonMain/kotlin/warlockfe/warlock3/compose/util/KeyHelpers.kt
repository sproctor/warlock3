package warlockfe.warlock3.compose.util

import androidx.compose.ui.input.key.Key

expect fun Key.getLabel(): String

private val allModifierKeys = arrayOf(
    Key.CtrlLeft,
    Key.CtrlRight,
    Key.AltLeft,
    Key.AltRight,
    Key.ShiftLeft,
    Key.ShiftRight,
    Key.MetaLeft,
    Key.MetaRight,
)

fun Key.isModifier(): Boolean {
    return allModifierKeys.contains(this)
}
