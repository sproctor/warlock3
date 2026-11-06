package warlockfe.warlock3.android

import android.app.Application
import androidx.room3.Room
import androidx.room3.RoomDatabase
import co.touchlab.kermit.Logger
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import warlockfe.warlock3.compose.AppContainer
import warlockfe.warlock3.compose.AppPlatform
import warlockfe.warlock3.compose.crash.CrashReporting
import warlockfe.warlock3.compose.observeSkin
import warlockfe.warlock3.compose.util.SentryCrashReporter
import warlockfe.warlock3.core.client.JavaProxy
import warlockfe.warlock3.core.client.WarlockProxy
import warlockfe.warlock3.core.prefs.PrefsDatabase
import warlockfe.warlock3.core.prefs.SettingsProblem
import warlockfe.warlock3.core.prefs.SettingsUnreadableException
import warlockfe.warlock3.core.util.AndroidSoundPlayer
import warlockfe.warlock3.core.util.WarlockDirs
import java.io.File

class WarlockApplication : Application() {
    lateinit var appContainer: AppContainer
        private set

    /**
     * Why the app cannot start, when the user's settings could not be read; then there is no
     * [appContainer], and the activity shows this instead of the app.
     */
    var settingsProblem: SettingsProblem? = null
        private set

    override fun onCreate() {
        super.onCreate()

        // Before anything else, so a crash during startup still gets reported -- but only if the
        // user has already said they want that; on a first launch nothing is sent until they answer
        // the notice the app shows. Debug builds never report: reports from someone's working tree
        // are noise.
        val crashReporting =
            CrashReporting(
                configDir = filesDir.path,
                dataDir = dataDir.path,
                fileSystem = SystemFileSystem,
                reporter =
                    SentryCrashReporter(
                        platform = "android",
                        version = BuildConfig.VERSION_NAME,
                        isRelease = !BuildConfig.DEBUG,
                    ),
            )
        crashReporting.startIfConsented()

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
        // Settings that cannot be read (the database or the config files) stop the app: starting
        // anyway would mean saving empty settings over the real ones. The activity says why.
        appContainer =
            try {
                runBlocking {
                    // Loads the config files, runs the DB->TOML migration and seeds default macros, on
                    // the IO dispatcher.
                    AppContainer.open(
                        platform =
                            AppPlatform(
                                warlockDirs = warlockDirs,
                                fileSystem = SystemFileSystem,
                                soundPlayer = AndroidSoundPlayer(),
                                warlockProxyFactory = WarlockProxy.Factory { JavaProxy(it) },
                                crashReporting = crashReporting,
                            ),
                        databaseDirectory = databaseDirectory,
                        databaseBuilder = ::getPrefsDatabaseBuilder,
                    )
                }
            } catch (e: SettingsUnreadableException) {
                Logger.e(e) { "Cannot start: the settings could not be read" }
                settingsProblem = e.problem
                return
            }
        // Here rather than in the activity: it runs on the app's scope, so starting it on every
        // activity recreation (each rotation) would stack up another watcher each time.
        appContainer.observeSkin(Logger.withTag("WarlockApplication")) { path ->
            File(path).takeIf { it.exists() }?.readBytes()
        }
    }

    private fun getPrefsDatabaseBuilder(filename: String): RoomDatabase.Builder<PrefsDatabase> =
        Room.databaseBuilder<PrefsDatabase>(
            context = this,
            name = filename,
        )
}
