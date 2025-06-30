package warlockfe.warlock3.compose.util

import androidx.compose.ui.input.key.Key
import warlockfe.warlock3.core.macro.MacroKeyCombo
import warlockfe.warlock3.core.prefs.MacroRepository

// TODO: refactor Macros to use a custom datatype and remove compose dependency
suspend fun MacroRepository.insertDefaultMacrosIfNeeded() {
    val globals = getGlobalCount()
    if (globals == 0) {
        put("global", MacroKeyCombo(Key.V.keyCode, ctrl = true), "{paste}")
        put("global", MacroKeyCombo(Key.C.keyCode, ctrl = true), "{copy}")
        put("global", MacroKeyCombo(Key.DirectionUp.keyCode), "{PrevHistory}")
        put("global", MacroKeyCombo(Key.DirectionDown.keyCode), "{NextHistory}")
        put("global", MacroKeyCombo(Key.MoveEnd.keyCode), "\\xsw\\r\\?")
        put("global", MacroKeyCombo(Key.DirectionDown.keyCode), "\\xs\\r\\?")
        put("global", MacroKeyCombo(Key.PageDown.keyCode), "\\xse\\r\\?")
        put("global", MacroKeyCombo(Key.DirectionLeft.keyCode), "\\xw\\r\\?")
        put("global", MacroKeyCombo(Key.DirectionCenter.keyCode), "\\xout\\r\\?")
        put("global", MacroKeyCombo(Key.DirectionRight.keyCode), "\\xe\\r\\?")
        put("global", MacroKeyCombo(Key.Home.keyCode), "\\xnw\\r\\?")
        put("global", MacroKeyCombo(Key.DirectionUp.keyCode), "\\xn\\r\\?")
        put("global", MacroKeyCombo(Key.PageUp.keyCode), "\\xne\\r\\?")
        put("global", MacroKeyCombo(Key.Insert.keyCode), "\\xdown\\r\\?")
        put("global",
            // this is currently broken on desktop, waiting for fix of https://youtrack.jetbrains.com/issue/CMP-4211
            MacroKeyCombo(Key.Delete.keyCode), "\\xup\\r\\?"
        )
        put("global", MacroKeyCombo(Key.Escape.keyCode), "{StopScript}")
        put("global", MacroKeyCombo(Key.Escape.keyCode, shift = true), "{PauseScript}")
        put("global", MacroKeyCombo(Key.NumPadEnter.keyCode), "{RepeatLast}")
        put("global", MacroKeyCombo(Key.PageUp.keyCode), "{PageUp}")
        put("global", MacroKeyCombo(Key.PageDown.keyCode), "{PageDown}")
    }
}

//expect val Key.Companion.NumPadInsert: Key
//expect val Key.Companion.NumPadDelete: Key
//expect val Key.Companion.End: Key
//expect val Key.Companion.NumPadDown: Key
//expect val Key.Companion.NumPadPageDown: Key