package warlockfe.warlock3.android

import android.app.Application
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import warlockfe.warlock3.android.di.AndroidAppContainer
import warlockfe.warlock3.compose.AppContainer
import warlockfe.warlock3.compose.util.insertDefaultMacrosIfNeeded
import warlockfe.warlock3.core.prefs.sql.Database
import warlockfe.warlock3.core.util.WarlockDirs
import warlockfe.warlock3.core.util.createDatabase

class WarlockApplication : Application() {

    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()

        val configDir = filesDir
        val warlockDirs = WarlockDirs(
            homeDir = filesDir.path,
            configDir = filesDir.path,
            dataDir = dataDir.path,
            logDir = filesDir.path + "/logs"
        )
        configDir.mkdirs()
        val driver = AndroidSqliteDriver(Database.Schema, this, "prefs.db")
        val database = createDatabase(driver)
        database.insertDefaultMacrosIfNeeded()
        appContainer = AndroidAppContainer(this, database, warlockDirs)
    }
}
