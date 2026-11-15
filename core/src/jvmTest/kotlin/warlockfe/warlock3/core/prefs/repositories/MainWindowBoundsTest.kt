package warlockfe.warlock3.core.prefs.repositories

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import warlockfe.warlock3.core.prefs.SettingsProblems
import warlockfe.warlock3.core.prefs.config.CharacterConfigStore
import warlockfe.warlock3.core.prefs.dao.CharacterSettingDao
import warlockfe.warlock3.core.prefs.models.CharacterSettingEntity
import java.nio.file.Files
import kotlin.io.path.deleteRecursively
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** A character's window bounds may have no position: Wayland never tells a client where its window is. */
class MainWindowBoundsTest {
    private val character = "dr:tholan"
    private val dir = Path(Files.createTempDirectory("main-window-bounds-test").toAbsolutePath().toString())

    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    @AfterTest
    fun tearDown() {
        java.nio.file.Path
            .of(dir.toString())
            .deleteRecursively()
    }

    private suspend fun repository(): CharacterSettingsRepository {
        val store = CharacterConfigStore(dir.toString(), SystemFileSystem).also { it.load() }
        return CharacterSettingsRepository(InMemoryCharacterSettingDao(), store, SettingsProblems(dir.toString()))
    }

    @Test
    fun boundsWithoutAPositionKeepThePositionSavedBefore() =
        runBlocking {
            val repository = repository()
            repository.saveMainWindowBounds(character, MainWindowBounds(x = 10, y = 20, width = 800, height = 600))
            repository.saveMainWindowBounds(character, MainWindowBounds(x = null, y = null, width = 900, height = 700))

            assertEquals(
                MainWindowBounds(x = 10, y = 20, width = 900, height = 700),
                repository.getMainWindowBounds(character),
            )
        }

    @Test
    fun boundsNeverSavedWithAPositionComeBackWithout() =
        runBlocking {
            val repository = repository()
            repository.saveMainWindowBounds(character, MainWindowBounds(x = null, y = null, width = 900, height = 700))

            assertEquals(
                MainWindowBounds(x = null, y = null, width = 900, height = 700),
                repository.getMainWindowBounds(character),
            )
        }

    @Test
    fun noBoundsWithoutASize() =
        runBlocking {
            assertNull(repository().getMainWindowBounds(character))
        }
}

private class InMemoryCharacterSettingDao : CharacterSettingDao {
    private val settings = mutableMapOf<Pair<String, String>, String>()

    override suspend fun getByKey(
        key: String,
        characterId: String,
    ): String? = settings[characterId to key]

    override fun observeByKey(
        key: String,
        characterId: String,
    ): Flow<String?> = error("unused")

    override suspend fun getByCharacter(characterId: String): List<CharacterSettingEntity> =
        settings.filterKeys { it.first == characterId }.map { (k, v) -> CharacterSettingEntity(k.first, k.second, v) }

    override suspend fun save(characterSetting: CharacterSettingEntity) {
        settings[characterSetting.characterId to characterSetting.key] = characterSetting.value
    }

    override suspend fun delete(
        key: String,
        characterId: String,
    ) {
        settings.remove(characterId to key)
    }

    override suspend fun deleteByCharacter(characterId: String) {
        settings.keys.removeAll { it.first == characterId }
    }
}
