package warlockfe.warlock3.compose

import androidx.room3.Room
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import warlockfe.warlock3.core.prefs.PrefsDatabase
import warlockfe.warlock3.core.prefs.SettingsUnreadableException
import warlockfe.warlock3.core.prefs.models.ClientSettingEntity
import java.nio.file.Files
import kotlin.io.path.deleteRecursively
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

/**
 * A preferences database that cannot be opened must stop the launch, with a reason the user can act
 * on -- not start the app on empty settings that the first save would then write over the real ones.
 *
 * Room builds lazily, so before this the first query was what opened the file, and at startup that
 * query runs on the main thread: a read-only volume became an app that would not launch and said
 * nothing about why (DESKTOP-3Y: `IllegalStateException: Unable to open database ...`, from
 * `runBlocking { clientSettings.getWidth() }`, with `Read-only file system` three causes down).
 */
class PrefsDatabaseOpenFailureTest {
    private lateinit var dir: java.nio.file.Path

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("prefs-open-failure-test")
    }

    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    @AfterTest
    fun tearDown() {
        dir.toFile().setWritable(true)
        dir.deleteRecursively()
    }

    private suspend fun open() =
        openPrefsDatabase(
            directory = Path(dir.toString()),
            fileSystem = SystemFileSystem,
            builderFactory = { filename -> Room.databaseBuilder<PrefsDatabase>(name = filename) },
        )

    @Test
    fun aDirectoryItCannotWriteToStopsTheLaunchWithAReasonTheUserCanAct0n() =
        runBlocking {
            // Room takes a cross-process lock on "<database>.lck" while it opens, so a directory it
            // cannot create files in fails the same way the read-only volume did.
            dir.toFile().setWritable(false)

            val thrown = assertFailsWith<SettingsUnreadableException> { open() }

            // The user gets the line that names the file and what the OS said, not Room's outermost
            // wrapper, which asks whether the developer configured a path correctly.
            assertContains(thrown.problem.reason, ".lck")
            assertFalse(
                "Was a proper path" in thrown.problem.reason,
                "expected the root cause, not Room's wrapper: ${thrown.problem.reason}",
            )
            // And it says where to look and why the app is closing rather than carrying on.
            assertContains(thrown.problem.message, dir.toString())
            assertContains(thrown.problem.message, "will close")
        }

    @Test
    fun aWritableDirectoryOpensTheRealDatabase() =
        runBlocking {
            val database = open()

            database.clientSettingDao().save(ClientSettingEntity("width", "1280"))
            assertEquals("1280", database.clientSettingDao().getByKey("width"))
            database.close()

            // A file on disk, which is the difference that matters: reopening it finds the setting.
            assertEquals("1280", open().clientSettingDao().getByKey("width"))
        }
}
