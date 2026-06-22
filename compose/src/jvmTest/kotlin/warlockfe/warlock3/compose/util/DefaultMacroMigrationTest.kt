package warlockfe.warlock3.compose.util

import androidx.compose.ui.input.key.Key
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.SystemFileSystem
import warlockfe.warlock3.compose.macros.KeyboardKeyMappings
import warlockfe.warlock3.core.macro.MacroKeyCombo
import warlockfe.warlock3.core.prefs.config.CharacterConfigStore
import warlockfe.warlock3.core.prefs.config.ClientConfigStore
import warlockfe.warlock3.core.prefs.dao.ClientSettingDao
import warlockfe.warlock3.core.prefs.dao.MacroDao
import warlockfe.warlock3.core.prefs.models.ClientSettingEntity
import warlockfe.warlock3.core.prefs.models.MacroEntity
import warlockfe.warlock3.core.prefs.repositories.ClientSettingRepository
import warlockfe.warlock3.core.prefs.repositories.MacroRepository
import warlockfe.warlock3.core.util.WarlockDirs
import java.nio.file.Files
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.io.files.Path as IoPath

class DefaultMacroMigrationTest {
    private val fs = SystemFileSystem
    private lateinit var dir: IoPath
    private lateinit var configDir: String

    @BeforeTest
    fun setUp() {
        dir = IoPath(Files.createTempDirectory("macro-migration-test").toAbsolutePath().toString())
        configDir = dir.toString()
    }

    @OptIn(ExperimentalPathApi::class)
    @AfterTest
    fun tearDown() {
        java.nio.file.Path
            .of(dir.toString())
            .deleteRecursively()
    }

    private fun newMacroRepo(store: CharacterConfigStore) =
        MacroRepository(StubMacroDao, store, KeyboardKeyMappings.keyCodeMap, KeyboardKeyMappings.reverseKeyCodeMap)

    private fun newClientSettings(store: ClientConfigStore) =
        ClientSettingRepository(StubClientSettingDao, store, WarlockDirs("", "", configDir, ""))

    private val ctrlR = MacroKeyCombo(Key.R.keyCode, ctrl = true)
    private val ctrlShiftR = MacroKeyCombo(Key.R.keyCode, ctrl = true, shift = true)

    @Test
    fun freshInstall_seedsAllDefaults_andSetsMarker() =
        runBlocking {
            val characterStore = CharacterConfigStore(configDir, fs).also { it.load() }
            val clientStore = ClientConfigStore(configDir, fs).also { it.load() }
            val repo = newMacroRepo(characterStore)
            val settings = newClientSettings(clientStore)

            repo.seedAndMigrateDefaultMacros(settings)

            val combos =
                repo
                    .observeGlobalMacros()
                    .first()
                    .map { it.keyCombo }
                    .toSet()
            assertTrue(defaultGlobalMacros.all { it.keyCombo in combos }, "all defaults should be seeded")
            assertEquals(defaultGlobalMacros.size, combos.size, "no extra or collapsed bindings")
            assertEquals(MACRO_DEFAULTS_VERSION, settings.getMacroDefaultsVersion())
        }

    @Test
    fun existingPreMarkerUser_addsNewDefaults_andDoesNotResurrectDeleted() =
        runBlocking {
            val characterStore = CharacterConfigStore(configDir, fs).also { it.load() }
            val clientStore = ClientConfigStore(configDir, fs).also { it.load() }
            val repo = newMacroRepo(characterStore)
            val settings = newClientSettings(clientStore)

            // Existing user with some v1 macros (so the store is non-empty) but missing the new v2
            // Ctrl+R pair, and with the v1 NumPad5 default deliberately absent (deleted). No marker.
            repo.put("global", MacroKeyCombo(Key.DirectionUp.keyCode), "{HistoryPrev}")
            repo.put("global", MacroKeyCombo(Key.NumPad2.keyCode), "\\xs\\r\\?")
            assertNull(settings.getMacroDefaultsVersion())

            repo.seedAndMigrateDefaultMacros(settings)

            val combos =
                repo
                    .observeGlobalMacros()
                    .first()
                    .map { it.keyCombo }
                    .toSet()
            assertTrue(ctrlR in combos, "new default added")
            assertTrue(ctrlShiftR in combos, "new default added")
            assertFalse(MacroKeyCombo(Key.NumPad5.keyCode) in combos, "deleted v1 default must not be resurrected")
            assertEquals(MACRO_DEFAULTS_VERSION, settings.getMacroDefaultsVersion())
        }

    @Test
    fun reboundNewKey_isPreserved() =
        runBlocking {
            val characterStore = CharacterConfigStore(configDir, fs).also { it.load() }
            val clientStore = ClientConfigStore(configDir, fs).also { it.load() }
            val repo = newMacroRepo(characterStore)
            val settings = newClientSettings(clientStore)

            // Existing user (no marker) who rebound Ctrl+R to their own action.
            repo.put("global", MacroKeyCombo(Key.DirectionUp.keyCode), "{HistoryPrev}")
            repo.put("global", ctrlR, "custom action")

            repo.seedAndMigrateDefaultMacros(settings)

            val macros = repo.observeGlobalMacros().first()
            assertEquals("custom action", macros.first { it.keyCombo == ctrlR }.action, "user binding wins")
            assertTrue(macros.any { it.keyCombo == ctrlShiftR }, "the other new default is still added")
        }

    @Test
    fun markerAtLatest_deletedNewDefaultNotReadded() =
        runBlocking {
            val characterStore = CharacterConfigStore(configDir, fs).also { it.load() }
            val clientStore = ClientConfigStore(configDir, fs).also { it.load() }
            val repo = newMacroRepo(characterStore)
            val settings = newClientSettings(clientStore)

            // Already migrated to the latest version, with the new default deleted by the user.
            repo.put("global", MacroKeyCombo(Key.DirectionUp.keyCode), "{HistoryPrev}")
            settings.putMacroDefaultsVersion(MACRO_DEFAULTS_VERSION)

            repo.seedAndMigrateDefaultMacros(settings)

            val combos =
                repo
                    .observeGlobalMacros()
                    .first()
                    .map { it.keyCombo }
                    .toSet()
            assertFalse(ctrlR in combos, "a deleted default must not return once the marker is current")
        }
}

private object StubMacroDao : MacroDao {
    override suspend fun getGlobalCount(): Int = error("unused")

    override suspend fun getOldMacros(): List<MacroEntity> = error("unused")

    override fun observeGlobals(): Flow<List<MacroEntity>> = error("unused")

    override fun observeByCharacter(characterId: String): Flow<List<MacroEntity>> = error("unused")

    override suspend fun getByCharacter(characterId: String): List<MacroEntity> = error("unused")

    override fun observeByCharacterWithGlobals(characterId: String): Flow<List<MacroEntity>> = error("unused")

    override suspend fun save(macro: MacroEntity) = error("unused")

    override suspend fun delete(
        characterId: String,
        keyString: String,
    ) = error("unused")

    override suspend fun deleteByKey(
        characterId: String,
        key: String,
    ) = error("unused")

    override suspend fun deleteAllGlobals() = error("unused")

    override suspend fun deleteByCharacter(characterId: String) = error("unused")
}

private object StubClientSettingDao : ClientSettingDao {
    override suspend fun getAll(): List<ClientSettingEntity> = error("unused")

    override suspend fun getByKey(key: String): String? = error("unused")

    override fun observeByKey(key: String): Flow<String?> = error("unused")

    override suspend fun removeByKey(key: String) = error("unused")

    override suspend fun save(entity: ClientSettingEntity) = error("unused")
}
