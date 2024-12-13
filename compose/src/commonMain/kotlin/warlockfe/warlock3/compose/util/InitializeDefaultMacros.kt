package warlockfe.warlock3.compose.util

import androidx.compose.ui.input.key.Key
import warlockfe.warlock3.compose.macros.getLabel
import warlockfe.warlock3.core.prefs.sql.Database
import warlockfe.warlock3.core.prefs.sql.Macro

private const val globalId = "global"

fun Database.insertDefaultMacrosIfNeeded() {
    val globals = macroQueries.getGlobals().executeAsList()
    if (globals.isEmpty()) {
        macroQueries.save(
            Macro(globalId, "ctrl+${Key.V.getLabel()}", "{paste}")
        )
        macroQueries.save(
            Macro(globalId, "ctrl+${Key.C.getLabel()}", "{copy}")
        )
        macroQueries.save(
            Macro(globalId, Key.DirectionUp.getLabel(), "{PrevHistory}")
        )
        macroQueries.save(
            Macro(globalId, Key.DirectionDown.getLabel(), "{NextHistory}")
        )
        macroQueries.save(
            Macro(globalId, Key.NumPad1.getLabel(), "\\xsw\\r?")
        )
        macroQueries.save(
            Macro(globalId, Key.NumPad2.getLabel(), "\\xs\\r?")
        )
        macroQueries.save(
            Macro(globalId, Key.NumPad3.getLabel(), "\\xse\\r?")
        )
        macroQueries.save(
            Macro(globalId, Key.NumPad4.getLabel(), "\\xw\\r?")
        )
        macroQueries.save(
            Macro(globalId, Key.NumPad5.getLabel(), "\\xout\\r?")
        )
        macroQueries.save(
            Macro(globalId, Key.NumPad6.getLabel(), "\\xe\\r?")
        )
        macroQueries.save(
            Macro(globalId, Key.NumPad7.getLabel(), "\\xnw\\r?")
        )
        macroQueries.save(
            Macro(globalId, Key.NumPad8.getLabel(), "\\xn\\r?")
        )
        macroQueries.save(
            Macro(globalId, Key.NumPad9.getLabel(), "\\xne\\r?")
        )
        macroQueries.save(
            Macro(globalId, Key.NumPad0.getLabel(), "\\xdown\\r?")
        )
        macroQueries.save(
            // this is currently broken on desktop, waiting for fix of https://github.com/JetBrains/compose-multiplatform/issues/4211
            Macro(globalId, Key.NumPadDot.getLabel(), "\\xup\\r?")
        )
        macroQueries.save(
            Macro(globalId, Key.Escape.getLabel(), "{StopScript}")
        )
        macroQueries.save(
            Macro(globalId, "shift+${Key.Escape.getLabel()}", "{PauseScript}")
        )
        macroQueries.save(
            Macro(globalId, Key.NumPadEnter.getLabel(), "{RepeatLast}")
        )
    }
}
