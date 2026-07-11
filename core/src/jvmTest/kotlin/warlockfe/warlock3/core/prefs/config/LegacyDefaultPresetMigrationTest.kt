package warlockfe.warlock3.core.prefs.config

import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import warlockfe.warlock3.core.text.WarlockColor
import java.nio.file.Files
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The legacy `presets["default"]` -> base-style fold that runs when a config is loaded. */
class LegacyDefaultPresetMigrationTest {
    private val fs = SystemFileSystem
    private lateinit var dir: Path
    private lateinit var configDir: String

    @BeforeTest
    fun setUp() {
        dir = Path(Files.createTempDirectory("preset-migration").toAbsolutePath().toString())
        configDir = dir.toString()
    }

    @OptIn(ExperimentalPathApi::class)
    @AfterTest
    fun tearDown() {
        java.nio.file.Path
            .of(dir.toString())
            .deleteRecursively()
    }

    private fun newStore() = CharacterConfigStore(configDir, fs)

    private val red = WarlockColor(red = 255, green = 0, blue = 0)
    private val blue = WarlockColor(red = 0, green = 0, blue = 255)
    private val green = WarlockColor(red = 0, green = 255, blue = 0)

    @Test
    fun legacyDefaultPresetFoldsIntoBaseAndIsDropped() =
        runBlocking {
            val id = "gs4:tholan"
            // A config that still has the old "default" preset alongside a real named preset.
            newStore().apply {
                load()
                mutate(id) {
                    it.copy(
                        presets =
                            mapOf(
                                "default" to PresetStyleConfig(textColor = red, backgroundColor = blue, bold = true),
                                "speech" to PresetStyleConfig(textColor = red),
                            ),
                    )
                }
            }

            // A fresh load runs the fold.
            val store = newStore()
            store.load()
            val config = store.current(id)

            assertFalse("default" in config.presets) // dropped
            assertTrue("speech" in config.presets) // named presets untouched
            assertEquals(red, config.settings.defaultTextColor)
            assertEquals(blue, config.settings.defaultBackgroundColor)
            assertEquals(700, config.settings.defaultFont?.weight) // bold -> weight 700

            // Persisted: a second fresh load sees the migrated state, not the "default" preset.
            val reloaded = newStore().apply { load() }.current(id)
            assertFalse("default" in reloaded.presets)
            assertEquals(red, reloaded.settings.defaultTextColor)
        }

    @Test
    fun existingBaseValuesWinOverTheLegacyDefault() =
        runBlocking {
            val id = "global"
            newStore().apply {
                load()
                mutate(id) {
                    it.copy(
                        presets = mapOf("default" to PresetStyleConfig(textColor = red)),
                        settings = it.settings.copy(defaultTextColor = green),
                    )
                }
            }

            val config = newStore().apply { load() }.current(id)
            // Base already set green; the legacy red must not clobber it.
            assertEquals(green, config.settings.defaultTextColor)
            assertFalse("default" in config.presets)
        }
}
