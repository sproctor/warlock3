package warlockfe.warlock3.core.prefs.repositories

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import warlockfe.warlock3.core.prefs.config.CharacterConfigStore
import warlockfe.warlock3.core.prefs.dao.WindowSettingsDao
import warlockfe.warlock3.core.prefs.models.WindowSettingsEntity
import warlockfe.warlock3.core.text.WarlockColor
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

            repository.openWindow(character, "combat", WindowLocation.RIGHT, 0)
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

            repository.openWindow(character, "combat", WindowLocation.RIGHT, 0)
            repository.closeWindow(character, "combat")
            repository.openWindow(character, "combat", WindowLocation.TOP, 0)

            assertFalse(repository.isHidden(character, "combat"))
            assertEquals(WindowLocation.TOP, repository.getWindowLocation(character, "combat"))
        }

    @Test
    fun theGameClosingAPanelDoesNotHideIt() =
        runBlocking {
            val dao = InMemoryWindowSettingsDao()
            val repository = newRepository(dao)

            repository.openWindow(character, "bank", WindowLocation.RIGHT, 0)
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

/**
 * Only the geometry queries this test needs; the rest of the DAO surface is unused here. [closeWindow]
 * and the other @Transaction methods come from the interface's own implementations.
 */
private class InMemoryWindowSettingsDao : WindowSettingsDao {
    private val rows = mutableMapOf<Pair<String, String>, WindowSettingsEntity>()

    override suspend fun openWindow(
        characterId: String,
        name: String,
        location: WindowLocation,
        position: Int,
    ) {
        val existing = rows[characterId to name]
        rows[characterId to name] =
            existing?.copy(location = location, position = position)
                ?: WindowSettingsEntity(
                    characterId = characterId,
                    name = name,
                    width = null,
                    height = null,
                    location = location,
                    position = position,
                    textColor = WarlockColor.Unspecified,
                    backgroundColor = WarlockColor.Unspecified,
                    fontFamily = null,
                    fontSize = null,
                    fontWeight = null,
                )
    }

    override suspend fun doCloseWindow(
        characterId: String,
        name: String,
    ) {
        rows[characterId to name]?.let { rows[characterId to name] = it.copy(location = null, position = null) }
    }

    override suspend fun getByName(
        characterId: String,
        name: String,
    ): WindowSettingsEntity? = rows[characterId to name]

    override suspend fun closeGap(
        characterId: String,
        location: WindowLocation?,
        position: Int?,
    ) = Unit

    override fun observeByCharacter(characterId: String): Flow<List<WindowSettingsEntity>> = error("unused")

    override suspend fun getByCharacter(characterId: String): List<WindowSettingsEntity> = error("unused")

    override suspend fun deleteByCharacter(characterId: String) = error("unused")

    override suspend fun save(windowSettings: WindowSettingsEntity) = error("unused")

    override suspend fun setStyle(
        characterId: String,
        name: String,
        textColor: WarlockColor,
        backgroundColor: WarlockColor,
        fontFamily: String?,
        fontSize: Float?,
        fontWeight: Int?,
    ) = error("unused")

    override suspend fun setNameFilter(
        characterId: String,
        name: String,
        nameFilter: Boolean,
    ) = error("unused")

    override suspend fun getByLocation(
        characterId: String,
        location: WindowLocation,
    ): List<WindowSettingsEntity> = error("unused")

    override suspend fun openGap(
        characterId: String,
        location: WindowLocation,
        position: Int,
    ) = error("unused")

    override suspend fun updateWidth(
        characterId: String,
        name: String,
        width: Int,
    ) = error("unused")

    override suspend fun updateHeight(
        characterId: String,
        name: String,
        height: Int,
    ) = error("unused")

    override suspend fun setPosition(
        characterId: String,
        name: String,
        pos: Int,
    ) = error("unused")
}
