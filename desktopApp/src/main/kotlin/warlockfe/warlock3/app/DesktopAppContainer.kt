package warlockfe.warlock3.app

import androidx.room3.Room
import androidx.room3.RoomDatabase
import ca.gosyer.appdirs.AppDirs
import co.touchlab.kermit.Logger
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.SystemFileSystem
import warlockfe.warlock3.compose.AppContainer
import warlockfe.warlock3.compose.AppPlatform
import warlockfe.warlock3.compose.crash.CrashReporting
import warlockfe.warlock3.core.client.JavaProxy
import warlockfe.warlock3.core.prefs.PrefsDatabase
import warlockfe.warlock3.core.prefs.SettingsUnreadableException
import warlockfe.warlock3.core.util.DesktopSoundPlayer
import warlockfe.warlock3.core.util.WarlockDirs
import java.io.File

/**
 * Opens the desktop [AppContainer] in the user's config directory, with its settings loaded,
 * blocking until they are. First moves a database left by the old MSIX install into place.
 *
 * @throws SettingsUnreadableException when the settings are on disk but cannot be read.
 */
internal fun openDesktopAppContainer(
    appDirs: AppDirs,
    crashReporting: CrashReporting,
    logger: Logger,
): AppContainer {
    val configDir = appDirs.getUserConfigDir()
    File(configDir).mkdirs()
    val databaseDirectory = kotlinx.io.files.Path(configDir)
    migrateLegacyWindowsPrefsDb(configDir, logger)

    val warlockDirs =
        WarlockDirs(
            homeDir = System.getProperty("user.home"),
            configDir = configDir,
            dataDir = appDirs.getUserDataDir(),
            logDir = appDirs.getUserLogDir(),
        )

    println("Loading preferences from $configDir")
    return runBlocking {
        AppContainer.open(
            platform =
                AppPlatform(
                    warlockDirs = warlockDirs,
                    fileSystem = SystemFileSystem,
                    soundPlayer = DesktopSoundPlayer(warlockDirs),
                    warlockProxyFactory = { JavaProxy(it) },
                    crashReporting = crashReporting,
                ),
            databaseDirectory = databaseDirectory,
            databaseBuilder = ::getPrefsDatabaseBuilder,
        )
    }
}

private fun getPrefsDatabaseBuilder(filename: String): RoomDatabase.Builder<PrefsDatabase> =
    Room.databaseBuilder<PrefsDatabase>(
        name = filename,
    )

private fun migrateLegacyWindowsPrefsDb(
    configDir: String,
    logger: Logger,
) {
    if (!System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)) {
        return
    }
    val configDirFile = File(configDir)
    val configDbFile = File(configDirFile, "prefs.db")
    if (configDbFile.exists()) {
        return
    }
    val snapshotPattern = Regex("""^warlock-v(\d+)\.db$""")
    val hasVersionedSnapshot =
        configDirFile.listFiles()?.any { it.isFile && snapshotPattern.matches(it.name) } ?: false
    if (hasVersionedSnapshot) {
        return
    }
    val localAppData =
        System.getenv("LOCALAPPDATA")
            ?: File(System.getProperty("user.home"), "AppData/Local").absolutePath
    // Conveyor shipped Warlock as an MSIX package, so the app ran sandboxed and its writes to
    // %LOCALAPPDATA%\WarlockFE\warlock were virtualized into the package's per-user LocalCache mirror at
    // %LOCALAPPDATA%\Packages\<PackageFamilyName>\LocalCache\Local\WarlockFE\warlock. The publisher-hash
    // suffix of the family name depends on the signing identity, so match any Warlock_* package directory;
    // if several exist (e.g. re-signed under a new identity), migrate the most recently written database.
    val legacyDb =
        File(localAppData, "Packages")
            .listFiles { file -> file.isDirectory && file.name.startsWith("Warlock_", ignoreCase = true) }
            ?.map { File(it, "LocalCache/Local/WarlockFE/warlock/prefs.db") }
            ?.filter { it.exists() }
            ?.maxByOrNull { it.lastModified() }
            ?: return
    val legacyDir = legacyDb.parentFile
    logger.i { "Migrating legacy prefs database from ${legacyDb.absolutePath} to ${configDbFile.absolutePath}" }
    try {
        legacyDb.copyTo(configDbFile, overwrite = false)
        listOf("prefs.db-wal", "prefs.db-shm").forEach { name ->
            val source = File(legacyDir, name)
            if (source.exists()) {
                source.copyTo(File(configDirFile, name), overwrite = false)
            }
        }
    } catch (e: Exception) {
        logger.e(e) { "Failed to migrate legacy prefs database" }
    }
}
