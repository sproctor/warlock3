package warlockfe.warlock3.core.prefs.repositories

import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import warlockfe.warlock3.core.prefs.InMemoryWindowSettingsDao
import warlockfe.warlock3.core.prefs.SettingsProblems
import warlockfe.warlock3.core.prefs.config.CharacterConfigStore
import warlockfe.warlock3.core.prefs.dao.WindowSettingsDao
import java.nio.file.Files
import kotlin.io.path.deleteRecursively
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    private suspend fun newRepository(dao: WindowSettingsDao) = WindowSettingsRepository(dao, newStore(), SettingsProblems(dir.toString()))

    private fun repositoryOn(
        dao: WindowSettingsDao,
        store: CharacterConfigStore,
    ) = WindowSettingsRepository(dao, store, SettingsProblems(dir.toString()))

    private suspend fun WindowSettingsDao.isOpen(name: String): Boolean = getByName(character, name)?.open == true

    @Test
    fun userClosingAWindowHidesIt() =
        runBlocking {
            val dao = InMemoryWindowSettingsDao()
            val repository = newRepository(dao)

            repository.openWindow(character, "combat")
            assertFalse(repository.isHidden(character, "combat"))
            assertTrue(dao.isOpen("combat"))

            repository.closeWindow(character, "combat")

            assertTrue(repository.isHidden(character, "combat"))
            assertFalse(dao.isOpen("combat"))
        }

    @Test
    fun askingForAWindowBackClearsHidden() =
        runBlocking {
            val dao = InMemoryWindowSettingsDao()
            val repository = newRepository(dao)

            repository.openWindow(character, "combat")
            repository.closeWindow(character, "combat")
            repository.openWindow(character, "combat")

            assertFalse(repository.isHidden(character, "combat"))
            assertTrue(dao.isOpen("combat"))
        }

    @Test
    fun theGameClosingAPanelDoesNotHideIt() =
        runBlocking {
            val dao = InMemoryWindowSettingsDao()
            val repository = newRepository(dao)

            repository.openWindow(character, "bank")
            repository.removeWindowFromLayout(character, "bank")

            // Out of the layout, but the game is free to open it again.
            assertFalse(dao.isOpen("bank"))
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

    @Test
    fun observedSettingsCarryTheOpenFlag() =
        runBlocking {
            val dao = InMemoryWindowSettingsDao()
            val repository = newRepository(dao)

            repository.openWindow(character, "combat")

            val row = dao.getByCharacter(character).single()
            assertEquals("combat", row.name)
            assertTrue(row.open)
        }
}
