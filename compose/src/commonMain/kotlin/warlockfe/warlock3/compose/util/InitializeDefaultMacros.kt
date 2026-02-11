package warlockfe.warlock3.compose.util

import androidx.compose.ui.input.key.Key
import warlockfe.warlock3.core.macro.MacroKeyCombo
import warlockfe.warlock3.core.prefs.repositories.MacroRepository

// TODO: refactor Macros to use a custom datatype and remove compose dependency
suspend fun MacroRepository.insertDefaultMacrosIfNeeded() {
    val globals = getGlobalCount()
    if (globals == 0) {
        put("global", MacroKeyCombo(Key.DirectionUp.keyCode), "{HistoryPrev}")
        put("global", MacroKeyCombo(Key.DirectionDown.keyCode), "{HistoryNext}")
        put("global", MacroKeyCombo(Key.NumPad1.keyCode), "\\xsw\\r\\?")
        put("global", MacroKeyCombo(Key.NumPad2.keyCode), "\\xs\\r\\?")
        put("global", MacroKeyCombo(Key.NumPad3.keyCode), "\\xse\\r\\?")
        put("global", MacroKeyCombo(Key.NumPad4.keyCode), "\\xw\\r\\?")
        put("global", MacroKeyCombo(Key.NumPad5.keyCode), "\\xout\\r\\?")
        put("global", MacroKeyCombo(Key.NumPad6.keyCode), "\\xe\\r\\?")
        put("global", MacroKeyCombo(Key.NumPad7.keyCode), "\\xnw\\r\\?")
        put("global", MacroKeyCombo(Key.NumPad8.keyCode), "\\xn\\r\\?")
        put("global", MacroKeyCombo(Key.NumPad9.keyCode), "\\xne\\r\\?")
        put("global", MacroKeyCombo(Key.NumPad0.keyCode), "\\xdown\\r\\?")
        put("global",
            // this is currently broken on desktop, waiting for fix of https://youtrack.jetbrains.com/issue/CMP-4211
            MacroKeyCombo(Key.NumPadDot.keyCode), "\\xup\\r\\?"
        )
        put("global", MacroKeyCombo(Key.Escape.keyCode), "{StopScript}")
        put("global", MacroKeyCombo(Key.Escape.keyCode, shift = true), "{PauseScript}")
        put("global", MacroKeyCombo(Key.NumPadEnter.keyCode), "{RepeatLast}")
        put("global", MacroKeyCombo(Key.PageUp.keyCode), "{PageUp}")
        put("global", MacroKeyCombo(Key.PageDown.keyCode), "{PageDown}")
        put("global", MacroKeyCombo(Key.U.keyCode, ctrl = true), "{ClearToStart}")
        put("global", MacroKeyCombo(Key.K.keyCode, ctrl = true), "{ClearToEnd}")
    }
}
