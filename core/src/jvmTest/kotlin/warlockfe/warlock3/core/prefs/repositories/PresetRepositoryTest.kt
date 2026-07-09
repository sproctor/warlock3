package warlockfe.warlock3.core.prefs.repositories

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import warlockfe.warlock3.core.prefs.config.CharacterConfigStore
import warlockfe.warlock3.core.prefs.config.GLOBAL_CHARACTER_ID
import warlockfe.warlock3.core.text.StyleDefinition
import warlockfe.warlock3.core.text.WarlockColor
import java.nio.file.Files
import kotlin.io.path.deleteRecursively
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PresetRepositoryTest {
    private val fs = SystemFileSystem
    private lateinit var dir: Path
    private lateinit var configDir: String

    @BeforeTest
    fun setUp() {
        dir = Path(Files.createTempDirectory("preset-test").toAbsolutePath().toString())
        configDir = dir.toString()
    }

    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    @AfterTest
    fun tearDown() {
        java.nio.file.Path
            .of(dir.toString())
            .deleteRecursively()
    }

    private val red = WarlockColor(red = 255, green = 0, blue = 0)
    private val green = WarlockColor(red = 0, green = 255, blue = 0)
    private val char = "gs4:tholan"

    @Test
    fun globalPresetsApplyWhenTheCharacterHasNoOverride() =
        runBlocking {
            val store = CharacterConfigStore(configDir, fs)
            store.load()
            val repo = PresetRepository(store)
            repo.saveGlobal("speech", StyleDefinition(textColor = green, bold = true))

            val forChar = repo.observeForCharacter(char).first()
            assertEquals(StyleDefinition(textColor = green, bold = true), forChar["speech"])
            // The character's own scope stays empty.
            assertEquals(emptyMap(), repo.observePresetsForCharacter(char).first())
        }

    @Test
    fun aCharacterPresetOverridesTheGlobalOneForThatKey() =
        runBlocking {
            val store = CharacterConfigStore(configDir, fs)
            store.load()
            val repo = PresetRepository(store)
            repo.saveGlobal("speech", StyleDefinition(textColor = green, bold = true))
            repo.saveGlobal("bold", StyleDefinition(bold = true))
            repo.save(char, "speech", StyleDefinition(textColor = red))

            val forChar = repo.observeForCharacter(char).first()
            // Character's speech wins; the global-only "bold" still applies.
            assertEquals(StyleDefinition(textColor = red), forChar["speech"])
            assertEquals(StyleDefinition(bold = true), forChar["bold"])
        }

    @Test
    fun globalScopeResolvesToOnlyGlobalPresets() =
        runBlocking {
            val store = CharacterConfigStore(configDir, fs)
            store.load()
            val repo = PresetRepository(store)
            repo.saveGlobal("speech", StyleDefinition(textColor = green))
            repo.save(char, "speech", StyleDefinition(textColor = red))

            assertEquals(
                StyleDefinition(textColor = green),
                repo.observeForCharacter(GLOBAL_CHARACTER_ID).first()["speech"],
            )
        }
}
