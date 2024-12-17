package warlockfe.warlock3.android

import android.app.Application
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.runBlocking
import warlockfe.warlock3.android.di.AndroidAppContainer
import warlockfe.warlock3.compose.AppContainer
import warlockfe.warlock3.compose.util.insertDefaultMacrosIfNeeded
import warlockfe.warlock3.core.prefs.PrefsDatabase
import warlockfe.warlock3.core.util.WarlockDirs

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
        val database = getPrefsDatabaseBuilder(getDatabasePath("prefs.db").absolutePath)
        appContainer = AndroidAppContainer(this, database, warlockDirs)
        runBlocking {
            appContainer.macroRepository.insertDefaultMacrosIfNeeded()
        }
    }

    private fun getPrefsDatabaseBuilder(filename: String): RoomDatabase.Builder<PrefsDatabase> {
        return Room.databaseBuilder<PrefsDatabase>(
            context = this,
            name = filename,
        )
    }
}
