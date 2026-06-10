package warlockfe.warlock3.core.prefs.config

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import warlockfe.warlock3.core.prefs.dao.CharacterDao
import warlockfe.warlock3.core.prefs.dao.HighlightDao
import warlockfe.warlock3.core.prefs.dao.NameDao
import warlockfe.warlock3.core.prefs.dao.VariableDao
import warlockfe.warlock3.core.prefs.models.CharacterEntity
import warlockfe.warlock3.core.prefs.models.HighlightEntity
import warlockfe.warlock3.core.prefs.models.HighlightStyleEntity
import warlockfe.warlock3.core.prefs.models.NameEntity
import warlockfe.warlock3.core.prefs.models.PopulatedHighlight
import warlockfe.warlock3.core.prefs.models.VariableEntity
import warlockfe.warlock3.core.prefs.repositories.HighlightRepositoryImpl
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

            val store = CharacterConfigStore(configDir, fs)
            store.load()
            val migration =
                ConfigMigration(
                    store = store,
                    characterDao = FakeCharacterDao(listOf(CharacterEntity(charId, "gs4", "Tholan"))),
                    highlightDao = FakeHighlightDao(highlights),
                    nameDao = FakeNameDao(emptyMap()),
                    variableDao = FakeVariableDao(variables),
                    fileSystem = fs,
                    configDirectory = configDir,
                )

            migration.migrateIfNeeded()

            // Data made it into the store / files
            val hl = HighlightRepositoryImpl(store).observeByCharacter(charId).first().single()
            assertEquals("char hl", hl.pattern)
            assertEquals(WarlockColor(red = 255, green = 0, blue = 0), hl.styles[0]?.textColor)
            assertEquals("ding.wav", hl.sound)

            val vars = VariableRepository(store).observeCharacterVariables(charId).first()
            assertEquals("100", vars.single().value)

            // Marker written
            assertTrue(fs.metadataOrNull(Path(Path(configDir), ".config-migrated")) != null)

            // Second run is a no-op even if the DAOs would now return different data
            val migration2 =
                ConfigMigration(
                    store = store,
                    characterDao = FakeCharacterDao(emptyList()),
                    highlightDao = FakeHighlightDao(emptyMap()),
                    nameDao = FakeNameDao(emptyMap()),
                    variableDao = FakeVariableDao(emptyMap()),
                    fileSystem = fs,
                    configDirectory = configDir,
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
