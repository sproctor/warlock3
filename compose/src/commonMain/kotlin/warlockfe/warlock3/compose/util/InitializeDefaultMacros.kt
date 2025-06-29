package warlockfe.warlock3.compose.util

import androidx.compose.ui.input.key.Key
import warlockfe.warlock3.core.prefs.MacroRepository

// TODO: refactor Macros to use a custom datatype and remove compose dependency
suspend fun MacroRepository.insertDefaultMacrosIfNeeded() {
    val globals = getGlobalMacros()
    if (globals.isEmpty()) {
        putGlobal("ctrl+${Key.V.keyCode}", "{paste}")
        putGlobal("ctrl+${Key.C.keyCode}", "{copy}")
        putGlobal(Key.DirectionUp.keyCode.toString(), "{PrevHistory}")
        putGlobal(Key.DirectionDown.keyCode.toString(), "{NextHistory}")
        putGlobal(Key.NumPad1.keyCode.toString(), "\\xsw\\r?")
        putGlobal(Key.NumPad2.keyCode.toString(), "\\xs\\r?")
        putGlobal(Key.NumPad3.keyCode.toString(), "\\xse\\r?")
        putGlobal(Key.NumPad4.keyCode.toString(), "\\xw\\r?")
        putGlobal(Key.NumPad5.keyCode.toString(), "\\xout\\r?")
        putGlobal(Key.NumPad6.keyCode.toString(), "\\xe\\r?")
        putGlobal(Key.NumPad7.keyCode.toString(), "\\xnw\\r?")
        putGlobal(Key.NumPad8.keyCode.toString(), "\\xn\\r?")
        putGlobal(Key.NumPad9.keyCode.toString(), "\\xne\\r?")
        putGlobal(Key.NumPad0.keyCode.toString(), "\\xdown\\r?")
        putGlobal(
            // this is currently broken on desktop, waiting for fix of https://youtrack.jetbrains.com/issue/CMP-4211
            Key.NumPadDot.keyCode.toString(), "\\xup\\r?"
        )
        putGlobal(Key.Escape.keyCode.toString(), "{StopScript}")
        putGlobal("shift+${Key.Escape.keyCode}", "{PauseScript}")
        putGlobal(Key.NumPadEnter.keyCode.toString(), "{RepeatLast}")
        putGlobal(Key.PageUp.keyCode.toString(), "{PageUp}")
        putGlobal(Key.PageDown.keyCode.toString(), "{PageDown}")
    }
}
