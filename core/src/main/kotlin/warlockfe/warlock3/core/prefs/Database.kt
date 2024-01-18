package warlockfe.warlock3.core.prefs

import warlockfe.warlock3.core.prefs.sql.Database
import warlockfe.warlock3.core.prefs.sql.Macro

private const val globalId = "global"

fun insertDefaultsIfNeeded(database: Database) {
    val macroQueries = database.macroQueries
    val globals = macroQueries.getGlobals().executeAsList()
    if (globals.isEmpty()) {
        macroQueries.save(
            Macro(globalId, "ctrl+369904058368", "{paste}")
        )
        macroQueries.save(
            Macro(globalId, "ctrl+288299679744", "{copy}")
        )
        macroQueries.save(
            Macro(globalId, "163745628160", "{PrevHistory}")
        )
        macroQueries.save(
            Macro(globalId, "172335562752", "{NextHistory}")
        )
        macroQueries.save(
            Macro(globalId, "152471339008", "\\xsw\\r?")
        )
        macroQueries.save(
            Macro(globalId, "968515125248", "\\xs\\r?")
        )
        macroQueries.save(
            Macro(globalId, "148176371712", "\\xse\\r?")
        )
        macroQueries.save(
            Macro(globalId, "972810092544", "\\xw\\r?")
        )
        macroQueries.save(
            Macro(globalId, "280755569688576", "\\xout\\r?")
        )
        macroQueries.save(
            Macro(globalId, "977105059840", "\\xe\\r?")
        )
        macroQueries.save(
            Macro(globalId, "156766306304", "\\xnw\\r?")
        )
        macroQueries.save(
            Macro(globalId, "964220157952", "\\xn\\r?")
        )
        macroQueries.save(
            Macro(globalId, "143881404416", "\\xne\\r?")
        )
        macroQueries.save(
            Macro(globalId, "116500987904", "{StopScript}")
        )
        macroQueries.save(
            Macro(globalId, "shift+116500987904", "{PauseScript}")
        )
        macroQueries.save(
            Macro(globalId, "45097156608", "{RepeatLast}")
        )
    }
}
