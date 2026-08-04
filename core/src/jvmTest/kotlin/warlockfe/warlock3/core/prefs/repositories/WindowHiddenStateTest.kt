package warlockfe.warlock3.core.prefs.repositories

import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import warlockfe.warlock3.core.prefs.InMemoryWindowSettingsDao
import warlockfe.warlock3.core.prefs.config.CharacterConfigStore
import warlockfe.warlock3.core.prefs.dao.WindowSettingsDao
import warlockfe.warlock3.core.window.WindowLocation
import java.nio.file.Files
import kotlin.io.path.deleteRecursively
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The hidden flag is what keeps a window the user closed from being reopened by the game, which
 * announces its panels with an `openDialog` on every login.
 */
class WindowHiddenStateTest {
    private val character = "dr:tholan"
    private lateinit var dir: Path

    @BeforeTest
    fun setUp() {
        dir = Path(Files.createTempDirectory("window-hidden-test").toAbsolutePath().toString())
    }

    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    @AfterTest
    fun tearDown() {
        java.nio.file.Path
            .of(dir.toString())
            .deleteRecursively()
    }

    private suspend fun newStore(): CharacterConfigStore = CharacterConfigStore(dir.toString(), SystemFileSystem).also { it.load() }

    private suspend fun newRepository(dao: WindowSettingsDao) = WindowSettingsRepository(dao, newStore())

    private fun repositoryOn(
        dao: WindowSettingsDao,
        store: CharacterConfigStore,
    ) = WindowSettingsRepository(dao, store)

    @Test
    fun userClosingAWindowHidesIt() =
        runBlocking {
            val dao = InMemoryWindowSettingsDao()
            val repository = newRepository(dao)

            repository.openWindowAtEnd(character, "combat", WindowLocation.RIGHT)
            assertFalse(repository.isHidden(character, "combat"))

            repository.closeWindow(character, "combat")

            assertTrue(repository.isHidden(character, "combat"))
            assertNull(repository.getWindowLocation(character, "combat"))
        }

    @Test
    fun askingForAWindowBackClearsHidden() =
        runBlocking {
            val dao = InMemoryWindowSettingsDao()
            val repository = newRepository(dao)

            repository.openWindowAtEnd(character, "combat", WindowLocation.RIGHT)
            repository.closeWindow(character, "combat")
            repository.openWindowAtEnd(character, "combat", WindowLocation.TOP)

            assertFalse(repository.isHidden(character, "combat"))
            assertEquals(WindowLocation.TOP, repository.getWindowLocation(character, "combat"))
        }

    @Test
    fun theGameClosingAPanelDoesNotHideIt() =
        runBlocking {
            val dao = InMemoryWindowSettingsDao()
            val repository = newRepository(dao)

            repository.openWindowAtEnd(character, "bank", WindowLocation.RIGHT)
            repository.removeWindowFromLayout(character, "bank")

            // Out of the layout, but the game is free to open it again.
            assertNull(repository.getWindowLocation(character, "bank"))
            assertFalse(repository.isHidden(character, "bank"))
        }

    @Test
    fun aWindowNeverPlacedIsNotHidden() =
        runBlocking {
            val repository = newRepository(InMemoryWindowSettingsDao())

            assertFalse(repository.isHidden(character, "neverSeen"))
        }

    @Test
    fun hiddenIsRememberedAcrossRestarts() =
        runBlocking {
            val dao = InMemoryWindowSettingsDao()
            newRepository(dao).closeWindow(character, "combat")

            // A fresh store reads the character's config files back from disk.
            val reloaded = repositoryOn(dao, newStore())

            assertTrue(reloaded.isHidden(character, "combat"))
        }
}
