package warlockfe.warlock3.android

import android.app.Application
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import warlockfe.warlock3.android.di.AndroidAppContainer
import warlockfe.warlock3.compose.AppContainer
import warlockfe.warlock3.compose.openPrefsDatabase
import warlockfe.warlock3.compose.util.insertDefaultMacrosIfNeeded
import warlockfe.warlock3.core.prefs.PrefsDatabase
import warlockfe.warlock3.core.util.WarlockDirs

class WarlockApplication : Application() {
    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()

        val configDir = filesDir
        val warlockDirs =
            WarlockDirs(
                homeDir = filesDir.path,
                configDir = filesDir.path,
                dataDir = dataDir.path,
                logDir = filesDir.path + "/logs",
            )
        configDir.mkdirs()

        val databaseDirectory = Path(getDatabasePath("placeholder").parentFile!!.absolutePath)
        val database =
            openPrefsDatabase(
                directory = databaseDirectory,
                fileSystem = SystemFileSystem,
                builderFactory = ::getPrefsDatabaseBuilder,
            )

        appContainer = AndroidAppContainer(database, warlockDirs, SystemFileSystem)
        runBlocking {
            appContainer.macroRepository.insertDefaultMacrosIfNeeded()
        }
    }

    private fun getPrefsDatabaseBuilder(filename: String): RoomDatabase.Builder<PrefsDatabase> =
        Room.databaseBuilder<PrefsDatabase>(
            context = this,
            name = filename,
        )
}
