package warlockfe.warlock3.android

import android.app.Application
import androidx.room3.Room
import androidx.room3.RoomDatabase
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import warlockfe.warlock3.compose.AppContainer
import warlockfe.warlock3.compose.openPrefsDatabase
import warlockfe.warlock3.compose.util.initializeSentry
import warlockfe.warlock3.core.client.JavaProxy
import warlockfe.warlock3.core.client.WarlockProxy
import warlockfe.warlock3.core.prefs.PrefsDatabase
import warlockfe.warlock3.core.util.AndroidSoundPlayer
import warlockfe.warlock3.core.util.WarlockDirs

class WarlockApplication : Application() {
    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()

        // Before anything else, so a crash during startup still gets reported. Debug builds are
        // skipped: reports from someone's working tree are noise.
        if (!BuildConfig.DEBUG) {
            initializeSentry(platform = "android", version = BuildConfig.VERSION_NAME)
        }

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

        // AppContainer loads config, runs the DB->TOML migration, and seeds default macros on init.
        appContainer =
            AppContainer(
                database = database,
                warlockDirs = warlockDirs,
                fileSystem = SystemFileSystem,
                soundPlayer = AndroidSoundPlayer(),
                warlockProxyFactory = WarlockProxy.Factory { JavaProxy(it) },
            )
    }

    private fun getPrefsDatabaseBuilder(filename: String): RoomDatabase.Builder<PrefsDatabase> =
        Room.databaseBuilder<PrefsDatabase>(
            context = this,
            name = filename,
        )
}
