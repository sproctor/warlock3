package warlockfe.warlock3.compose.util

import androidx.compose.ui.input.key.Key
import warlockfe.warlock3.core.prefs.sql.Database
import warlockfe.warlock3.core.prefs.sql.Macro

private const val globalId = "global"

fun Database.insertDefaultMacrosIfNeeded() {
    val globals = macroQueries.getGlobals().executeAsList()
    if (globals.isEmpty()) {
        macroQueries.save(
            Macro(globalId, "ctrl+${Key.V.keyCode}", "{paste}")
        )
        macroQueries.save(
            Macro(globalId, "ctrl+${Key.C.keyCode}", "{copy}")
        )
        macroQueries.save(
            Macro(globalId, Key.DirectionUp.keyCode.toString(), "{PrevHistory}")
        )
        macroQueries.save(
            Macro(globalId, Key.DirectionDown.keyCode.toString(), "{NextHistory}")
        )
        macroQueries.save(
            Macro(globalId, Key.NumPad1.keyCode.toString(), "\\xsw\\r?")
        )
        macroQueries.save(
            Macro(globalId, Key.NumPad2.keyCode.toString(), "\\xs\\r?")
        )
        macroQueries.save(
            Macro(globalId, Key.NumPad3.keyCode.toString(), "\\xse\\r?")
        )
        macroQueries.save(
            Macro(globalId, Key.NumPad4.keyCode.toString(), "\\xw\\r?")
        )
        macroQueries.save(
            Macro(globalId, Key.NumPad5.keyCode.toString(), "\\xout\\r?")
        )
        macroQueries.save(
            Macro(globalId, Key.NumPad6.keyCode.toString(), "\\xe\\r?")
        )
        macroQueries.save(
            Macro(globalId, Key.NumPad7.keyCode.toString(), "\\xnw\\r?")
        )
        macroQueries.save(
            Macro(globalId, Key.NumPad8.keyCode.toString(), "\\xn\\r?")
        )
        macroQueries.save(
            Macro(globalId, Key.NumPad9.keyCode.toString(), "\\xne\\r?")
        )
        macroQueries.save(
            Macro(globalId, Key.NumPad0.keyCode.toString(), "\\xdown\\r?")
        )
        macroQueries.save(
            // this is currently broken on desktop, waiting for fix of https://github.com/JetBrains/compose-multiplatform/issues/4211
            Macro(globalId, Key.NumPadDot.keyCode.toString(), "\\xup\\r?")
        )
        macroQueries.save(
            Macro(globalId, Key.Escape.keyCode.toString(), "{StopScript}")
        )
        macroQueries.save(
            Macro(globalId, "shift+${Key.Escape.keyCode}", "{PauseScript}")
        )
        macroQueries.save(
            Macro(globalId, Key.NumPadEnter.keyCode.toString(), "{RepeatLast}")
        )
    }
}
