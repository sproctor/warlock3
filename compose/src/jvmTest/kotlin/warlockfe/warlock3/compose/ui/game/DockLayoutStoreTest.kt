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
    fun aLayoutEncodedOnLeavingTheCompositionIsTheOneSaved() =
        runTest {
            val container = testAppContainer(dir, StandardTestDispatcher(testScheduler))
            val store = DockLayoutStore(MutableStateFlow("char"), container.characterSettingsRepository)
            var onScreen = "before leaving"
            var spotsDirty = true

            store.recordLayout { onScreen }
            // Like DockSpotMemory: encoding the spots marks them saved.
            store.recordSpots { if (spotsDirty) "spots".also { spotsDirty = false } else null }
            store.encodeRecorded()
            // The state this read is gone; whatever it would say now is not what was on screen.
            onScreen = "after leaving"
            store.flush()

            assertEquals("before leaving", store.loadLayout())
            assertEquals("spots", store.loadSpots())
        }
}
