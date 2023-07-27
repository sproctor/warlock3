package cc.warlock.warlock3.core.prefs

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import cc.warlock.warlock3.core.prefs.sql.Database
import cc.warlock.warlock3.core.prefs.sql.Macro
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val versionPragma = "user_version"

suspend fun migrateIfNeeded(driver: SqlDriver, fileName: String) {
    val oldVersion =
        driver.executeQuery(null, "PRAGMA $versionPragma", { cursor ->
            QueryResult.Value(
                if (cursor.next().value) {
                    cursor.getLong(0)
                } else {
                    null
                } ?: 0L
            )
        }, 0).await()

    val newVersion = Database.Schema.version

    if (oldVersion == 0L) {
        println("Creating DB version $newVersion!")
        Database.Schema.create(driver)
        driver.execute(null, "PRAGMA $versionPragma=$newVersion", 0)
    } else if (oldVersion < newVersion) {
        println("Migrating DB from version $oldVersion to $newVersion!")
        var i = 0
        var backupFile: File
        while (true) {
            backupFile = File(fileName + if (i > 0) ".bak.$i" else ".bak")
            i++
            if (!backupFile.exists()) break
        }
        File(fileName).copyTo(backupFile)
        Database.Schema.migrate(driver, oldVersion, newVersion)
        driver.execute(null, "PRAGMA $versionPragma=$newVersion", 0)
    } else if (oldVersion > newVersion) {
        throw RuntimeException("preferences DB file is from a newer version of warlock")
    }
}

private const val globalId = "global"

suspend fun insertDefaultsIfNeeded(database: Database) {
    withContext(Dispatchers.IO) {
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
}