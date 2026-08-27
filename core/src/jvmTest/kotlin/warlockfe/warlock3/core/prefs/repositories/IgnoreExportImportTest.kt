package warlockfe.warlock3.core.prefs.repositories

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import warlockfe.warlock3.core.prefs.config.CharacterConfigStore
import warlockfe.warlock3.core.prefs.config.ClientConfigStore
import warlockfe.warlock3.core.prefs.dao.AccountDao
import warlockfe.warlock3.core.prefs.dao.CharacterSettingDao
import warlockfe.warlock3.core.prefs.dao.ClientSettingDao
import warlockfe.warlock3.core.prefs.dao.ScriptDirDao
import warlockfe.warlock3.core.prefs.dao.WindowSettingsDao
import warlockfe.warlock3.core.prefs.export.CharacterExport
import warlockfe.warlock3.core.prefs.models.CharacterSettingEntity
import warlockfe.warlock3.core.prefs.models.Ignore
import warlockfe.warlock3.core.prefs.models.IgnoreMatchMode
import warlockfe.warlock3.core.prefs.models.ScriptDirEntity
import warlockfe.warlock3.core.prefs.models.WindowSettingsEntity
import java.nio.file.Files
import kotlin.io.path.deleteRecursively
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

/** Ignores must survive the JSON export/import round trip, with pattern as the MERGE key. */
class IgnoreExportImportTest {
    private val fs = SystemFileSystem
    private lateinit var dir: Path
    private lateinit var configDir: String
    private lateinit var store: CharacterConfigStore

    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    @BeforeTest
    fun setUp() {
        dir = Path(Files.createTempDirectory("ignore-export-test").toAbsolutePath().toString())
        configDir = dir.toString()
        store = CharacterConfigStore(configDir, fs)
    }

    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    @AfterTest
    fun tearDown() {
        java.nio.file.Path
            .of(dir.toString())
            .deleteRecursively()
    }

    private fun newRepository() =
        ExportRepository(
            accountDao = UnusedAccountDao,
            characterSettingDao = EmptyCharacterSettingDao,
            clientSettingDao = UnusedClientSettingDao,
            scriptDirDao = EmptyScriptDirDao,
            windowSettingsDao = EmptyWindowSettingsDao,
            characterConfigStore = store,
            clientConfigStore = ClientConfigStore(configDir, fs),
        )

    private fun ignore(
        pattern: String,
        mode: IgnoreMatchMode = IgnoreMatchMode.CONTAINS,
        isRegex: Boolean = false,
        ignoreCase: Boolean = false,
    ) = Ignore(id = Uuid.random(), pattern = pattern, isRegex = isRegex, matchMode = mode, ignoreCase = ignoreCase)

    // Serialize through Json in between, as SettingsTransferUseCase does.
    private fun CharacterExport.throughJson(): CharacterExport =
        json.decodeFromString(CharacterExport.serializer(), json.encodeToString(CharacterExport.serializer(), this))

    @Test
    fun ignoresRoundTripThroughExportAndReplaceImport() =
        runBlocking {
            store.load()
            val ignoreRepository = IgnoreRepository(store)
            ignoreRepository.save("gs4:tholan", ignore("spammy line", mode = IgnoreMatchMode.LINE, ignoreCase = true))
            ignoreRepository.save("gs4:tholan", ignore("^You gained", isRegex = true))

            val repository = newRepository()
            val export = repository.getCharacterExport("gs4:tholan").throughJson()
            assertEquals(listOf("spammy line", "^You gained"), export.ignores.map { it.pattern })
            assertEquals("line", export.ignores[0].mode)

            repository.importCharacter(export, "gs4:other", ImportMode.REPLACE)
            val imported = IgnoreRepository(store).observeByCharacter("gs4:other").first()
            assertEquals(
                listOf(
                    Triple("spammy line", IgnoreMatchMode.LINE, true),
                    Triple("^You gained", IgnoreMatchMode.CONTAINS, false),
                ),
                imported.map { Triple(it.pattern, it.matchMode, it.ignoreCase) },
            )
            assertEquals(listOf(false, true), imported.map { it.isRegex })
        }

    @Test
    fun mergeImportDedupesIgnoresByPattern() =
        runBlocking {
            store.load()
            val ignoreRepository = IgnoreRepository(store)
            ignoreRepository.save("gs4:source", ignore("spam", mode = IgnoreMatchMode.LINE))
            ignoreRepository.save("gs4:source", ignore("other"))
            ignoreRepository.save("gs4:target", ignore("spam", mode = IgnoreMatchMode.WORD))
            ignoreRepository.save("gs4:target", ignore("kept"))

            val repository = newRepository()
            val export = repository.getCharacterExport("gs4:source").throughJson()
            repository.importCharacter(export, "gs4:target", ImportMode.MERGE)

            val merged = IgnoreRepository(store).observeByCharacter("gs4:target").first()
            assertEquals(
                mapOf("kept" to IgnoreMatchMode.CONTAINS, "spam" to IgnoreMatchMode.LINE, "other" to IgnoreMatchMode.CONTAINS),
                merged.associate { it.pattern to it.matchMode },
            )
        }

    @Test
    fun exportWrittenBeforeIgnoresExistedLoadsAsEmpty() =
        runBlocking {
            store.load()
            IgnoreRepository(store).save("gs4:tholan", ignore("spam"))
            val full =
                json.encodeToJsonElement(
                    CharacterExport.serializer(),
                    newRepository().getCharacterExport("gs4:tholan"),
                ) as JsonObject
            val legacy = JsonObject(full.filterKeys { it != "ignores" })
            val parsed = json.decodeFromJsonElement(CharacterExport.serializer(), legacy)
            assertEquals(emptyList(), parsed.ignores)
        }
}

private object UnusedAccountDao : AccountDao {
    override suspend fun getAll() = error("unused")

    override fun observeAll(): Flow<List<warlockfe.warlock3.core.prefs.models.AccountEntity>> = error("unused")

    override suspend fun getByUsername(username: String) = error("unused")

    override suspend fun save(account: warlockfe.warlock3.core.prefs.models.AccountEntity) = error("unused")

    override suspend fun insertIfAbsent(account: warlockfe.warlock3.core.prefs.models.AccountEntity) = error("unused")

    override suspend fun delete(username: String) = error("unused")
}

private object UnusedClientSettingDao : ClientSettingDao {
    override suspend fun getAll() = error("unused")

    override suspend fun getByKey(key: String): String? = error("unused")

    override fun observeByKey(key: String): Flow<String?> = error("unused")

    override suspend fun removeByKey(key: String) = error("unused")

    override suspend fun save(entity: warlockfe.warlock3.core.prefs.models.ClientSettingEntity) = error("unused")
}

private object EmptyCharacterSettingDao : CharacterSettingDao {
    override suspend fun getByKey(
        key: String,
        characterId: String,
    ): String? = null

    override fun observeByKey(
        key: String,
        characterId: String,
    ): Flow<String?> = error("unused")

    override suspend fun getByCharacter(characterId: String): List<CharacterSettingEntity> = emptyList()

    override suspend fun save(characterSetting: CharacterSettingEntity) = Unit

    override suspend fun delete(
        key: String,
        characterId: String,
    ) = Unit

    override suspend fun deleteByCharacter(characterId: String) = Unit
}

private object EmptyScriptDirDao : ScriptDirDao {
    override fun observeByCharacter(characterId: String): Flow<List<String>> = error("unused")

    override suspend fun getByCharacterWithGlobal(characterId: String): List<String> = emptyList()

    override suspend fun getByCharacter(characterId: String): List<String> = emptyList()

    override suspend fun save(scriptDir: ScriptDirEntity) = Unit

    override suspend fun delete(
        characterId: String,
        path: String,
    ) = Unit

    override suspend fun deleteByCharacter(characterId: String) = Unit
}

private object EmptyWindowSettingsDao : WindowSettingsDao {
    override fun observeByCharacter(characterId: String): Flow<List<WindowSettingsEntity>> = error("unused")

    override suspend fun getByCharacter(characterId: String): List<WindowSettingsEntity> = emptyList()

    override suspend fun getByName(
        characterId: String,
        name: String,
    ): WindowSettingsEntity? = null

    override suspend fun save(windowSettings: WindowSettingsEntity) = Unit

    override suspend fun openWindow(
        characterId: String,
        name: String,
    ) = Unit

    override suspend fun closeWindow(
        characterId: String,
        name: String,
    ) = Unit

    override suspend fun deleteByCharacter(characterId: String) = Unit
}
