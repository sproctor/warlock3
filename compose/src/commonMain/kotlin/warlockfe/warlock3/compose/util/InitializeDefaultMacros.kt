package warlockfe.warlock3.compose.util

import androidx.compose.ui.input.key.Key
import warlockfe.warlock3.compose.macros.getLabel
import warlockfe.warlock3.core.prefs.MacroRepository

// TODO: refactor Macros to use a custom datatype and remove compose dependency
suspend fun MacroRepository.insertDefaultMacrosIfNeeded() {
    val globals = getGlobalMacros()
    if (globals.isEmpty()) {
        putGlobal("ctrl+${Key.V.getLabel()}", "{paste}")
        putGlobal("ctrl+${Key.C.getLabel()}", "{copy}")
        putGlobal(Key.DirectionUp.getLabel(), "{PrevHistory}")
        putGlobal(Key.DirectionDown.getLabel(), "{NextHistory}")
        putGlobal(Key.NumPad1.getLabel(), "\\xsw\\r?")
        putGlobal(Key.NumPad2.getLabel(), "\\xs\\r?")
        putGlobal(Key.NumPad3.getLabel(), "\\xse\\r?")
        putGlobal(Key.NumPad4.getLabel(), "\\xw\\r?")
        putGlobal(Key.NumPad5.getLabel(), "\\xout\\r?")
        putGlobal(Key.NumPad6.getLabel(), "\\xe\\r?")
        putGlobal(Key.NumPad7.getLabel(), "\\xnw\\r?")
        putGlobal(Key.NumPad8.getLabel(), "\\xn\\r?")
        putGlobal(Key.NumPad9.getLabel(), "\\xne\\r?")
        putGlobal(Key.NumPad0.getLabel(), "\\xdown\\r?")
        putGlobal(
            // this is currently broken on desktop, waiting for fix of https://github.com/JetBrains/compose-multiplatform/issues/4211
            Key.NumPadDot.getLabel(), "\\xup\\r?"
        )
        putGlobal(Key.Escape.getLabel(), "{StopScript}")
        putGlobal("shift+${Key.Escape.getLabel()}", "{PauseScript}")
        putGlobal(Key.NumPadEnter.getLabel(), "{RepeatLast}")
    }
}
