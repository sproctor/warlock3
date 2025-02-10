package warlockfe.warlock3.compose.macros

import androidx.compose.ui.input.key.Key
import io.github.oshai.kotlinlogging.KotlinLogging

private val keyMappings = mapOf(
    Key.F1 to "F1",
    Key.F2 to "F2",
    Key.F3 to "F3",
    Key.F4 to "F4",
    Key.F5 to "F5",
    Key.F6 to "F6",
    Key.F7 to "F7",
    Key.F8 to "F8",
    Key.F9 to "F9",
    Key.F10 to "F10",
    Key.F11 to "F11",
    Key.F12 to "F12",
    Key.Escape to "Esc",
    Key.PrintScreen to "PrtSc",
    Key.ScrollLock to "ScrLk",
    Key.Break to "Brk",
    Key.Tab to "Tab",
    Key.CapsLock to "CapsLock",
    Key.Enter to "Enter",
    Key.ShiftLeft to "Shift",
    Key.CtrlLeft to "Ctrl",
    Key.MetaLeft to "Meta",
    Key.AltLeft to "Alt",
    Key.Spacebar to "Spacebar",
    Key.AltRight to "Alt",
    Key.MetaRight to "Meta",
    Key.CtrlRight to "Ctrl",
    Key.Insert to "Ins",
    Key.MoveHome to "Home",
    Key.PageUp to "PgUp",
    Key.Delete to "Del",
    Key.MoveEnd to "End",
    Key.PageDown to "PgDn",
    Key.NumLock to "NumLock",
    Key.DirectionUp to "Up",
    Key.DirectionDown to "Down",
    Key.DirectionLeft to "Left",
    Key.DirectionRight to "Right",
    Key.Grave to "`",
    Key.One to "1",
    Key.Two to "2",
    Key.Three to "3",
    Key.Four to "4",
    Key.Five to "5",
    Key.Six to "6",
    Key.Seven to "7",
    Key.Eight to "8",
    Key.Nine to "9",
    Key.Zero to "0",
    Key.Minus to "Minus",
    Key.Equals to "Equals",
    Key.Backspace to "Backspace",
    Key.A to "A",
    Key.B to "B",
    Key.C to "C",
    Key.D to "D",
    Key.E to "E",
    Key.F to "F",
    Key.G to "G",
    Key.H to "H",
    Key.I to "I",
    Key.J to "J",
    Key.K to "K",
    Key.L to "L",
    Key.M to "M",
    Key.N to "N",
    Key.O to "O",
    Key.P to "P",
    Key.Q to "Q",
    Key.R to "R",
    Key.S to "S",
    Key.T to "T",
    Key.U to "U",
    Key.V to "V",
    Key.W to "W",
    Key.X to "X",
    Key.Y to "Y",
    Key.Z to "Z",
    Key.NumPad0 to "NP0",
    Key.NumPad1 to "NP1",
    Key.NumPad2 to "NP2",
    Key.NumPad3 to "NP3",
    Key.NumPad4 to "NP4",
    Key.NumPad5 to "NP5",
    Key.NumPad6 to "NP6",
    Key.NumPad7 to "NP7",
    Key.NumPad8 to "NP8",
    Key.NumPad9 to "NP9",
    Key.NumPadDivide to "NPDivide",
    Key.NumPadMultiply to "NPMultiply",
    Key.NumPadSubtract to "NPMinus",
    Key.NumPadAdd to "NPAdd",
    Key.NumPadDot to "NPDot",
    Key.NumPadEnter to "NPEnter",
)

val reverseKeyMappings = keyMappings.entries.associate { (k, v) -> v to k }

fun Key.getLabel(): String {
    val text = keyMappings.firstNotNullOfOrNull {
        if (it.key.keyCode == keyCode) {
            it.value
        } else {
            null
        }
    }
    if (text == null) {
        KotlinLogging.logger("key").debug { "unassociated key: $this" }
    }
    return text ?: "??"
}