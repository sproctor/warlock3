package warlockfe.warlock3.core.prefs.config

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import warlockfe.warlock3.core.prefs.dao.AliasDao
import warlockfe.warlock3.core.prefs.dao.AlterationDao
import warlockfe.warlock3.core.prefs.dao.CharacterDao
import warlockfe.warlock3.core.prefs.dao.CharacterSettingDao
import warlockfe.warlock3.core.prefs.dao.ClientSettingDao
import warlockfe.warlock3.core.prefs.dao.ConnectionDao
import warlockfe.warlock3.core.prefs.dao.HighlightDao
import warlockfe.warlock3.core.prefs.dao.MacroDao
import warlockfe.warlock3.core.prefs.dao.NameDao
import warlockfe.warlock3.core.prefs.dao.PresetStyleDao
import warlockfe.warlock3.core.prefs.dao.ProgressBarSettingDao
import warlockfe.warlock3.core.prefs.dao.VariableDao
import warlockfe.warlock3.core.prefs.dao.WindowSettingsDao
import warlockfe.warlock3.core.prefs.models.AliasEntity
import warlockfe.warlock3.core.prefs.models.CharacterEntity
import warlockfe.warlock3.core.prefs.models.CharacterSettingEntity
import warlockfe.warlock3.core.prefs.models.ClientSettingEntity
import warlockfe.warlock3.core.prefs.models.ConnectionWithSettings
import warlockfe.warlock3.core.prefs.models.HighlightEntity
import warlockfe.warlock3.core.prefs.models.HighlightStyleEntity
import warlockfe.warlock3.core.prefs.models.MacroEntity
import warlockfe.warlock3.core.prefs.models.NameEntity
import warlockfe.warlock3.core.prefs.models.PopulatedHighlight
import warlockfe.warlock3.core.prefs.models.PresetStyleEntity
import warlockfe.warlock3.core.prefs.models.ProgressBarSettingEntity
import warlockfe.warlock3.core.prefs.models.VariableEntity
import warlockfe.warlock3.core.prefs.models.WindowSettingsEntity
import warlockfe.warlock3.core.prefs.repositories.AliasRepository
import warlockfe.warlock3.core.prefs.repositories.HighlightRepositoryImpl
import warlockfe.warlock3.core.prefs.repositories.MacroRepository
import warlockfe.warlock3.core.prefs.repositories.VariableRepository
import warlockfe.warlock3.core.text.WarlockColor
import java.nio.file.Files
import kotlin.io.path.deleteRecursively
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class ConfigMigrationTest {
    private val fs = SystemFileSystem
    private lateinit var dir: Path
    private lateinit var configDir: String

    @BeforeTest
    fun setUp() {
        dir = Path(Files.createTempDirectory("migration-test").toAbsolutePath().toString())
        configDir = dir.toString()
    }

    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    @AfterTest
    fun tearDown() {
        java.nio.file.Path
            .of(dir.toString())
            .deleteRecursively()
    }

    @Test
    fun migration_copiesRowsToFiles_andRunsOnce() =
        runBlocking {
            val charId = "gs4:tholan"
            val highlightId = Uuid.random()
            val highlights =
                mapOf(
                    "global" to
                        listOf(
                            PopulatedHighlight(
                                highlight = HighlightEntity(Uuid.random(), "global", "global hl", false, false, true, null),
                                styles = emptyList(),
                            ),
                        ),
                    charId to
                        listOf(
                            PopulatedHighlight(
                                highlight = HighlightEntity(highlightId, charId, "char hl", true, false, false, "ding.wav"),
                                styles =
                                    listOf(
                                        HighlightStyleEntity(
                                            highlightId = highlightId,
                                            groupNumber = 0,
                                            textColor = WarlockColor(red = 255, green = 0, blue = 0),
                                            backgroundColor = WarlockColor.Unspecified,
                                            entireLine = false,
                                            bold = true,
                                            italic = false,
                                            underline = false,
                                            fontFamily = null,
                                            fontSize = null,
                                            fontWeight = null,
                                        ),
                                    ),
                            ),
                        ),
                )
            val variables = mapOf(charId to listOf(VariableEntity(charId, "health", "100")))
            val aliases = mapOf(charId to listOf(AliasEntity(Uuid.random(), charId, "k", "kill")))
            val clientSettings = listOf(ClientSettingEntity("theme", "DARK"))

            val store = CharacterConfigStore(configDir, fs)
            store.load()
            val clientStore = ClientConfigStore(configDir, fs)
            clientStore.load()
            val migration =
                buildMigration(
                    store = store,
                    clientStore = clientStore,
                    characters = listOf(CharacterEntity(charId, "gs4", "Tholan")),
                    highlights = highlights,
                    variables = variables,
                    aliases = aliases,
                    clientSettings = clientSettings,
                )

            migration.migrateIfNeeded()

            // Per-character data made it into the store / files
            val hl = HighlightRepositoryImpl(store).observeByCharacter(charId).first().single()
            assertEquals("char hl", hl.pattern)
            assertEquals(WarlockColor(red = 255, green = 0, blue = 0), hl.styles[0]?.textColor)
            assertEquals("ding.wav", hl.sound)

            val vars = VariableRepository(store).observeCharacterVariables(charId).first()
            assertEquals("100", vars.single().value)

            val alias = AliasRepository(store).observeByCharacter(charId).first().single()
            assertEquals("k", alias.pattern)
            assertEquals("kill", alias.replacement)

            // Client-wide settings made it into client.toml
            assertEquals("DARK", clientStore.currentClient().theme)

            // client.toml written (this file's presence is what guards re-running the migration)
            assertTrue(fs.metadataOrNull(Path(Path(configDir), "client.toml")) != null)

            // Second run is a no-op even if the DAOs would now return different data
            val migration2 =
                buildMigration(
                    store = store,
                    clientStore = clientStore,
                    characters = emptyList(),
                    highlights = emptyMap(),
                    variables = emptyMap(),
                    aliases = emptyMap(),
                    clientSettings = emptyList(),
                )
            migration2.migrateIfNeeded()
            // Data still present (not wiped by the second, empty migration)
            assertEquals(
                "char hl",
                HighlightRepositoryImpl(store)
                    .observeByCharacter(charId)
                    .first()
                    .single()
                    .pattern,
            )
        }

    private fun buildMigration(
        store: CharacterConfigStore,
        clientStore: ClientConfigStore,
        characters: List<CharacterEntity>,
        highlights: Map<String, List<PopulatedHighlight>>,
        variables: Map<String, List<VariableEntity>>,
        aliases: Map<String, List<AliasEntity>>,
        clientSettings: List<ClientSettingEntity>,
    ): ConfigMigration {
        val macroDao = FakeMacroDao()
        return ConfigMigration(
            store = store,
            clientConfigStore = clientStore,
            characterDao = FakeCharacterDao(characters),
            highlightDao = FakeHighlightDao(highlights),
            nameDao = FakeNameDao(emptyMap()),
            variableDao = FakeVariableDao(variables),
            aliasDao = FakeAliasDao(aliases),
            alterationDao = FakeAlterationDao(),
            presetStyleDao = FakePresetStyleDao(),
            progressBarSettingDao = FakeProgressBarSettingDao(),
            windowSettingsDao = FakeWindowSettingsDao(),
            characterSettingDao = FakeCharacterSettingDao(),
            clientSettingDao = FakeClientSettingDao(clientSettings),
            connectionDao = FakeConnectionDao(),
            macroRepository = MacroRepository(macroDao, store, emptyMap(), emptyMap()),
            fileSystem = fs,
            configDirectory = configDir,
        )
    }
}

private class FakeCharacterDao(
    private val all: List<CharacterEntity>,
) : CharacterDao {
    override suspend fun getAll(): List<CharacterEntity> = all

    override suspend fun getById(id: String): CharacterEntity? = all.firstOrNull { it.id == id }

    override fun observeAll(): Flow<List<CharacterEntity>> = flowOf(all)

    override suspend fun save(character: CharacterEntity) = error("unused")

    override suspend fun delete(id: String) = error("unused")
}

private class FakeHighlightDao(
    private val byCharacter: Map<String, List<PopulatedHighlight>>,
) : HighlightDao {
    override suspend fun getHighlightsByCharacter(characterId: String): List<PopulatedHighlight> = byCharacter[characterId] ?: emptyList()

    override fun observeHighlightsByCharacter(characterId: String): Flow<List<PopulatedHighlight>> = error("unused")

    override fun observeHighlightsForCharacter(characterId: String): Flow<List<PopulatedHighlight>> = error("unused")

    override suspend fun deleteByPattern(
        pattern: String,
        characterId: String,
    ) = error("unused")

    override suspend fun deleteById(id: Uuid) = error("unused")

    override suspend fun deleteByCharacter(characterId: String) = error("unused")

    override suspend fun save(highlight: HighlightEntity) = error("unused")

    override suspend fun save(highlightStyleEntities: List<HighlightStyleEntity>) = error("unused")
}

private class FakeNameDao(
    private val byCharacter: Map<String, List<NameEntity>>,
) : NameDao {
    override suspend fun getByCharacter(characterId: String): List<NameEntity> = byCharacter[characterId] ?: emptyList()

    override fun observeNamesByCharacter(characterId: String): Flow<List<NameEntity>> = error("unused")

    override fun observeNamesForCharacter(characterId: String): Flow<List<NameEntity>> = error("unused")

    override suspend fun deleteByText(
        text: String,
        characterId: String,
    ) = error("unused")

    override suspend fun deleteById(id: Uuid) = error("unused")

    override suspend fun deleteByCharacter(characterId: String) = error("unused")

    override suspend fun save(name: NameEntity) = error("unused")

    override suspend fun save(names: List<NameEntity>) = error("unused")
}

private class FakeVariableDao(
    private val byCharacter: Map<String, List<VariableEntity>>,
) : VariableDao {
    override suspend fun getAllByCharacter(characterId: String): List<VariableEntity> = byCharacter[characterId] ?: emptyList()

    override fun observeByCharacter(characterId: String): Flow<List<VariableEntity>> = error("unused")

    override suspend fun delete(
        characterId: String,
        name: String,
    ) = error("unused")

    override suspend fun deleteByCharacter(characterId: String) = error("unused")

    override suspend fun save(variable: VariableEntity) = error("unused")
}

private class FakeAliasDao(
    private val byCharacter: Map<String, List<AliasEntity>>,
) : AliasDao {
    override suspend fun getByCharacter(characterId: String): List<AliasEntity> = byCharacter[characterId] ?: emptyList()

    override fun observeByCharacterWithGlobals(characterId: String): Flow<List<AliasEntity>> = error("unused")

    override fun observeByCharacter(characterId: String): Flow<List<AliasEntity>> = error("unused")

    override suspend fun save(alias: AliasEntity) = error("unused")

    override suspend fun delete(id: Uuid) = error("unused")

    override suspend fun deleteByCharacter(characterId: String) = error("unused")
}

private class FakeAlterationDao : AlterationDao {
    override suspend fun getAlterationsByCharacter(characterId: String): List<warlockfe.warlock3.core.prefs.models.AlterationEntity> =
        emptyList()

    override fun observeAlterationsByCharacter(characterId: String): Flow<List<warlockfe.warlock3.core.prefs.models.AlterationEntity>> =
        error("unused")

    override fun observeAlterationsByCharacterWithGlobals(
        characterId: String,
    ): Flow<List<warlockfe.warlock3.core.prefs.models.AlterationEntity>> = error("unused")

    override suspend fun deleteById(id: Uuid) = error("unused")

    override suspend fun deleteByCharacter(characterId: String) = error("unused")

    override suspend fun save(alteration: warlockfe.warlock3.core.prefs.models.AlterationEntity) = error("unused")
}

private class FakePresetStyleDao : PresetStyleDao {
    override fun observeByCharacter(characterId: String): Flow<List<PresetStyleEntity>> = error("unused")

    override suspend fun getByCharacter(characterId: String): List<PresetStyleEntity> = emptyList()

    override suspend fun deleteByCharacter(characterId: String) = error("unused")

    override suspend fun save(presetStyle: PresetStyleEntity) = error("unused")
}

private class FakeProgressBarSettingDao : ProgressBarSettingDao {
    override fun observeByCharacter(characterId: String): Flow<List<ProgressBarSettingEntity>> = error("unused")

    override suspend fun getByCharacter(characterId: String): List<ProgressBarSettingEntity> = emptyList()

    override suspend fun save(setting: ProgressBarSettingEntity) = error("unused")

    override suspend fun delete(
        characterId: String,
        id: String,
    ) = error("unused")
}

private class FakeCharacterSettingDao : CharacterSettingDao {
    override suspend fun getByKey(
        key: String,
        characterId: String,
    ): String? = null

    override fun observeByKey(
        key: String,
        characterId: String,
    ): Flow<String?> = error("unused")

    override suspend fun getByCharacter(characterId: String): List<CharacterSettingEntity> = emptyList()

    override suspend fun save(characterSetting: CharacterSettingEntity) = error("unused")

    override suspend fun delete(
        key: String,
        characterId: String,
    ) = error("unused")

    override suspend fun deleteByCharacter(characterId: String) = error("unused")
}

private class FakeClientSettingDao(
    private val all: List<ClientSettingEntity>,
) : ClientSettingDao {
    override suspend fun getAll(): List<ClientSettingEntity> = all

    override suspend fun getByKey(key: String): String? = all.firstOrNull { it.key == key }?.value

    override fun observeByKey(key: String): Flow<String?> = error("unused")

    override suspend fun removeByKey(key: String) = error("unused")

    override suspend fun save(entity: ClientSettingEntity) = error("unused")
}

private class FakeConnectionDao : ConnectionDao {
    override suspend fun getByName(name: String): ConnectionWithSettings? = null

    override fun observeAllWithDetails(): Flow<List<ConnectionWithSettings>> = error("unused")

    override suspend fun getAllWithSettings(): List<ConnectionWithSettings> = emptyList()

    override suspend fun save(connection: warlockfe.warlock3.core.prefs.models.ConnectionEntity) = error("unused")

    override suspend fun delete(id: String) = error("unused")

    override suspend fun rename(
        oldName: String,
        newName: String,
    ) = error("unused")

    override suspend fun renameById(
        id: String,
        newName: String,
    ) = error("unused")
}

private class FakeMacroDao : MacroDao {
    override suspend fun getGlobalCount(): Int = 0

    override suspend fun getOldMacros(): List<MacroEntity> = emptyList()

    override fun observeGlobals(): Flow<List<MacroEntity>> = error("unused")

    override fun observeByCharacter(characterId: String): Flow<List<MacroEntity>> = error("unused")

    override suspend fun getByCharacter(characterId: String): List<MacroEntity> = emptyList()

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

private class FakeWindowSettingsDao : WindowSettingsDao {
    override fun observeByCharacter(characterId: String): Flow<List<WindowSettingsEntity>> = error("unused")

    override suspend fun getByCharacter(characterId: String): List<WindowSettingsEntity> = emptyList()

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

    override suspend fun openWindow(
        characterId: String,
        name: String,
        location: warlockfe.warlock3.core.window.WindowLocation,
        position: Int,
    ) = error("unused")

    override suspend fun doCloseWindow(
        characterId: String,
        name: String,
    ) = error("unused")

    override suspend fun getByLocation(
        characterId: String,
        location: warlockfe.warlock3.core.window.WindowLocation,
    ): List<WindowSettingsEntity> = error("unused")

    override suspend fun getByName(
        characterId: String,
        name: String,
    ): WindowSettingsEntity? = error("unused")

    override suspend fun openGap(
        characterId: String,
        location: warlockfe.warlock3.core.window.WindowLocation,
        position: Int,
    ) = error("unused")

    override suspend fun closeGap(
        characterId: String,
        location: warlockfe.warlock3.core.window.WindowLocation?,
        position: Int?,
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
