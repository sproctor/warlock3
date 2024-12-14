package warlockfe.warlock3.compose.util

import androidx.compose.ui.input.key.Key
import warlockfe.warlock3.compose.macros.getLabel
import warlockfe.warlock3.core.prefs.sql.Macro
import warlockfe.warlock3.core.prefs.sql.MacroQueries

private const val globalId = "global"

// TODO: refactor Macros to use a custom datatype and remove compose dependency
fun MacroQueries.insertDefaultMacrosIfNeeded() {
    val globals = getGlobals().executeAsList()
    if (globals.isEmpty()) {
        save(
            Macro(globalId, "ctrl+${Key.V.getLabel()}", "{paste}")
        )
        save(
            Macro(globalId, "ctrl+${Key.C.getLabel()}", "{copy}")
        )
        save(
            Macro(globalId, Key.DirectionUp.getLabel(), "{PrevHistory}")
        )
        save(
            Macro(globalId, Key.DirectionDown.getLabel(), "{NextHistory}")
        )
        save(
            Macro(globalId, Key.NumPad1.getLabel(), "\\xsw\\r?")
        )
        save(
            Macro(globalId, Key.NumPad2.getLabel(), "\\xs\\r?")
        )
        save(
            Macro(globalId, Key.NumPad3.getLabel(), "\\xse\\r?")
        )
        save(
            Macro(globalId, Key.NumPad4.getLabel(), "\\xw\\r?")
        )
        save(
            Macro(globalId, Key.NumPad5.getLabel(), "\\xout\\r?")
        )
        save(
            Macro(globalId, Key.NumPad6.getLabel(), "\\xe\\r?")
        )
        save(
            Macro(globalId, Key.NumPad7.getLabel(), "\\xnw\\r?")
        )
        save(
            Macro(globalId, Key.NumPad8.getLabel(), "\\xn\\r?")
        )
        save(
            Macro(globalId, Key.NumPad9.getLabel(), "\\xne\\r?")
        )
        save(
            Macro(globalId, Key.NumPad0.getLabel(), "\\xdown\\r?")
        )
        save(
            // this is currently broken on desktop, waiting for fix of https://github.com/JetBrains/compose-multiplatform/issues/4211
            Macro(globalId, Key.NumPadDot.getLabel(), "\\xup\\r?")
        )
        save(
            Macro(globalId, Key.Escape.getLabel(), "{StopScript}")
        )
        save(
            Macro(globalId, "shift+${Key.Escape.getLabel()}", "{PauseScript}")
        )
        save(
            Macro(globalId, Key.NumPadEnter.getLabel(), "{RepeatLast}")
        )
    }
}
