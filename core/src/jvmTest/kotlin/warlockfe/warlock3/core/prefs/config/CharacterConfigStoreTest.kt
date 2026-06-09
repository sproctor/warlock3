package warlockfe.warlock3.core.prefs.config

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlinx.io.writeString
import warlockfe.warlock3.core.prefs.models.Highlight
import warlockfe.warlock3.core.prefs.models.NameEntity
import warlockfe.warlock3.core.prefs.models.VariableEntity
import warlockfe.warlock3.core.prefs.repositories.HighlightRepositoryImpl
import warlockfe.warlock3.core.prefs.repositories.NameRepositoryImpl
import warlockfe.warlock3.core.prefs.repositories.VariableRepository
import warlockfe.warlock3.core.text.StyleDefinition
import warlockfe.warlock3.core.text.WarlockColor
import java.nio.file.Files
import kotlin.io.path.deleteRecursively
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class CharacterConfigStoreTest {
    private val fs = SystemFileSystem
    private lateinit var dir: Path
    private lateinit var configDir: String

    @BeforeTest
    fun setUp() {
        dir = Path(Files.createTempDirectory("config-test").toAbsolutePath().toString())
        configDir = dir.toString()
    }

    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    @AfterTest
    fun tearDown() {
        java.nio.file.Path
            .of(dir.toString())
            .deleteRecursively()
    }

    private fun newStore() = CharacterConfigStore(configDir, fs)

    private fun readFile(path: Path): String = fs.source(path).buffered().use { it.readByteArray().decodeToString() }

    private fun redHighlight(pattern: String): Highlight =
        Highlight(
            id = Uuid.random(),
            pattern = pattern,
            styles = mapOf(0 to StyleDefinition(textColor = WarlockColor(red = 255, green = 0, blue = 0), bold = true)),
            isRegex = false,
            matchPartialWord = false,
            ignoreCase = true,
            sound = null,
        )

    @Test
    fun highlight_saved_isPersistedAndReloadable() =
        runBlocking {
            val store = newStore()
            store.load()
            val repo = HighlightRepositoryImpl(store)

            val highlight = redHighlight("you are bleeding")
            repo.save("gs4:tholan", highlight)

            // Observable immediately
            val observed = repo.observeByCharacter("gs4:tholan").first()
            assertEquals(1, observed.size)
            assertEquals("you are bleeding", observed.single().pattern)
            assertTrue(observed.single().ignoreCase)

            // Written to the character's file with a human-friendly hex color, not a raw argb long
            val file = Path(Path(Path(configDir, "characters"), "gs4"), "tholan.toml")
            val text = readFile(file)
            assertTrue(text.contains("#ffff0000"), "expected hex color in file, was:\n$text")
            assertTrue(!text.contains("argb"), "raw argb should not leak into file:\n$text")

            // A fresh store reads it back identically
            val reloaded = newStore()
            reloaded.load()
            val afterReload = HighlightRepositoryImpl(reloaded).observeByCharacter("gs4:tholan").first()
            assertEquals(observed, afterReload)
        }

    @Test
    fun highlight_deleteById_findsOwningCharacter() =
        runBlocking {
            val store = newStore()
            store.load()
            val repo = HighlightRepositoryImpl(store)
            val highlight = redHighlight("foo")
            repo.save("gs4:tholan", highlight)

            repo.deleteById(highlight.id)

            assertTrue(repo.observeByCharacter("gs4:tholan").first().isEmpty())
        }

    @Test
    fun highlight_observeForCharacter_mergesGlobalAndCharacter() =
        runBlocking {
            val store = newStore()
            store.load()
            val repo = HighlightRepositoryImpl(store)
            repo.saveGlobal(redHighlight("global one"))
            repo.save("gs4:tholan", redHighlight("character one"))

            val merged =
                repo
                    .observeForCharacter("gs4:tholan")
                    .first()
                    .map { it.pattern }
                    .toSet()
            assertEquals(setOf("global one", "character one"), merged)
        }

    @Test
    fun highlight_upsert_replacesSamePattern() =
        runBlocking {
            val store = newStore()
            store.load()
            val repo = HighlightRepositoryImpl(store)
            repo.save("gs4:tholan", redHighlight("dup"))
            repo.save("gs4:tholan", redHighlight("dup")) // different id, same pattern

            assertEquals(1, repo.observeByCharacter("gs4:tholan").first().size)
        }

    @Test
    fun name_roundTrips() =
        runBlocking {
            val store = newStore()
            store.load()
            val repo = NameRepositoryImpl(store)
            val name =
                NameEntity(
                    id = Uuid.random(),
                    characterId = "gs4:tholan",
                    text = "Tholan",
                    textColor = WarlockColor(red = 0, green = 128, blue = 255),
                    backgroundColor = WarlockColor.Unspecified,
                    bold = false,
                    italic = true,
                    underline = false,
                    fontFamily = null,
                    fontSize = null,
                    fontWeight = null,
                    sound = null,
                )
            repo.save(name)

            val reloaded = newStore()
            reloaded.load()
            val result = NameRepositoryImpl(reloaded).observeByCharacter("gs4:tholan").first().single()
            assertEquals("Tholan", result.text)
            assertEquals(name.textColor, result.textColor)
            assertTrue(result.italic)
        }

    @Test
    fun variables_putObserveDelete() =
        runBlocking {
            val store = newStore()
            store.load()
            val repo = VariableRepository(store)
            repo.put("gs4:tholan", "health", "100")
            repo.put("gs4:tholan", "mana", "50")

            assertEquals(
                mapOf("health" to "100", "mana" to "50"),
                repo.observeCharacterVariables("gs4:tholan").first().associate { it.name to it.value },
            )

            repo.delete("gs4:tholan", "mana")
            assertEquals(
                listOf(VariableEntity("gs4:tholan", "health", "100")),
                repo.observeCharacterVariables("gs4:tholan").first(),
            )

            // Variables share the character file with highlights/names without clobbering it
            val reloaded = newStore()
            reloaded.load()
            assertEquals(
                "100",
                VariableRepository(reloaded)
                    .observeCharacterVariables("gs4:tholan")
                    .first()
                    .single()
                    .value,
            )
        }

    @Test
    fun handAuthoredEntryWithoutId_getsStableIdOnLoad() =
        runBlocking {
            // Simulate a user hand-writing a file with no id field.
            val charsDir = Path(Path(configDir, "characters"), "gs4")
            fs.createDirectories(charsDir)
            val file = Path(charsDir, "tholan.toml")
            fs.sink(file).buffered().use {
                it.writeString(
                    """
                    character = "gs4:tholan"

                    [[highlights]]
                    pattern = "ouch"
                    ignoreCase = true

                      [[highlights.styles]]
                      group = 0
                      bold = true
                    """.trimIndent(),
                )
            }

            val store = newStore()
            store.load()
            val highlight = HighlightRepositoryImpl(store).observeByCharacter("gs4:tholan").first().single()
            assertEquals("ouch", highlight.pattern)

            // The load should have written an id back into the file so it's stable next launch.
            val text = readFile(file)
            assertTrue(text.contains("id ="), "expected a generated id to be persisted, was:\n$text")

            // Reloading yields the same id (stable, not regenerated each launch).
            val reloaded = newStore()
            reloaded.load()
            val again = HighlightRepositoryImpl(reloaded).observeByCharacter("gs4:tholan").first().single()
            assertEquals(highlight.id, again.id)
        }

    @Test
    fun emptyStore_observesEmpty() =
        runBlocking {
            val store = newStore()
            store.load()
            assertTrue(HighlightRepositoryImpl(store).observeByCharacter("gs4:nobody").first().isEmpty())
            assertNull(store.snapshot()["gs4:nobody"])
        }
}
