package warlockfe.warlock3.compose.ui.game

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import warlockfe.warlock3.compose.deleteTestDir
import warlockfe.warlock3.compose.testAppContainer
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DockLayoutStoreTest {
    private lateinit var dir: java.nio.file.Path

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("dock-layout-store-test")
    }

    @AfterTest
    fun tearDown() {
        deleteTestDir(dir)
    }

    @Test
    fun aRecordedLayoutIsEncodedOnceWhenSaved() =
        runTest {
            val container = testAppContainer(dir, StandardTestDispatcher(testScheduler))
            val store = DockLayoutStore(MutableStateFlow("char"), container.characterSettingsRepository)
            var encodings = 0

            repeat(3) { frame ->
                store.recordLayout {
                    encodings++
                    "layout $frame"
                }
            }
            assertEquals(0, encodings, "recording a change must not encode it")
            store.flush()

            assertEquals(1, encodings)
            assertEquals("layout 2", store.loadLayout())
        }

    @Test
    fun loadingSavesAChangeRecordedByTheStateItReplaces() =
        runTest {
            val container = testAppContainer(dir, StandardTestDispatcher(testScheduler))
            val store = DockLayoutStore(MutableStateFlow("char"), container.characterSettingsRepository)
            var spotsDirty = true

            // Recorded by a docking state that left before its debounced save.
            store.recordLayout { "moved" }
            // Like DockSpotMemory: encoding the spots marks them saved.
            store.recordSpots { if (spotsDirty) "spots".also { spotsDirty = false } else null }

            assertEquals("moved", store.loadLayout())
            assertEquals("spots", store.loadSpots())
        }
}
