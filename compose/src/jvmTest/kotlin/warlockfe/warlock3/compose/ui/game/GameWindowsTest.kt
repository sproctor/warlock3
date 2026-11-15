package warlockfe.warlock3.compose.ui.game

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.SystemFileSystem
import warlockfe.warlock3.compose.deleteTestDir
import warlockfe.warlock3.compose.testAppContainer
import warlockfe.warlock3.core.client.WarlockAction
import warlockfe.warlock3.core.prefs.SettingsProblems
import warlockfe.warlock3.core.prefs.config.CharacterConfigStore
import warlockfe.warlock3.core.prefs.dao.WindowSettingsDao
import warlockfe.warlock3.core.prefs.models.WindowSettingsEntity
import warlockfe.warlock3.core.prefs.repositories.WindowSettingsRepository
import warlockfe.warlock3.core.window.WindowInfo
import warlockfe.warlock3.core.window.WindowType
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The open-window list, which the game and the user both change. The game's opens and closes
 * suspend before acting - on the character being named, then on the saved layout being restored -
 * so the order they land in is the thing worth pinning down.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GameWindowsTest {
    private lateinit var dir: java.nio.file.Path

    // The windows' own coroutines, cancelled after each test: they collect settings forever.
    private val job = Job()

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("game-windows-test")
    }

    @AfterTest
    fun tearDown() {
        job.cancel()
        deleteTestDir(dir)
    }

    /** The window layout rows, in memory, so saving them happens in test time. */
    private class FakeWindowSettingsDao : WindowSettingsDao {
        val rows = MutableStateFlow<Map<Pair<String, String>, WindowSettingsEntity>>(emptyMap())

        override fun observeByCharacter(characterId: String): Flow<List<WindowSettingsEntity>> =
            rows.map { all -> all.values.filter { it.characterId == characterId }.sortedBy { it.name } }

        override suspend fun getByCharacter(characterId: String) =
            rows.value.values
                .filter { it.characterId == characterId }
                .sortedBy { it.name }

        override suspend fun getByName(
            characterId: String,
            name: String,
        ) = rows.value[characterId to name]

        override suspend fun deleteByCharacter(characterId: String) {
            rows.value = rows.value.filterKeys { it.first != characterId }
        }

        override suspend fun save(windowSettings: WindowSettingsEntity) {
            rows.value += (windowSettings.characterId to windowSettings.name) to windowSettings
        }

        override suspend fun openWindow(
            characterId: String,
            name: String,
        ) {
            val existing = rows.value[characterId to name] ?: WindowSettingsEntity(characterId = characterId, name = name)
            rows.value += (characterId to name) to existing.copy(open = true)
        }

        override suspend fun closeWindow(
            characterId: String,
            name: String,
        ) {
            val existing = rows.value[characterId to name] ?: return
            rows.value += (characterId to name) to existing.copy(open = false)
        }

        fun isOpen(name: String) = rows.value[CHARACTER to name]?.open == true
    }

    private val dao = FakeWindowSettingsDao()

    private lateinit var client: FakeWarlockClient
    private lateinit var repository: WindowSettingsRepository

    private suspend fun TestScope.windows(): GameWindows {
        // Only for its window registry. Its background work, a config-file watcher among it, runs
        // on real threads under [job], so cancelling that releases the watcher: left open, enough
        // of them exhaust the user's inotify instances for every later test in the JVM.
        val registry = testAppContainer(dir.resolve("app"), Dispatchers.IO, parentJob = job).windowRegistryFactory.create()
        client = FakeWarlockClient(registry)
        val store = CharacterConfigStore(dir.resolve("config").toString(), SystemFileSystem)
        repository = WindowSettingsRepository(dao, store, SettingsProblems(dir.toString()))
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + job)
        val settings =
            client.characterId
                .observePerCharacter { repository.observeWindowSettings(it) }
                .stateIn(scope, SharingStarted.Eagerly, emptyList())
        return GameWindows(client, registry, repository, settings, scope)
    }

    private fun GameWindows.names() = windowUiStates.value.map { it.name }

    private fun panel(
        name: String,
        resident: Boolean,
    ) = WindowInfo(
        name = name,
        title = name,
        subtitle = null,
        windowType = WindowType.PANEL,
        showTimestamps = false,
        backgroundImage = null,
        resident = resident,
    )

    @Test
    fun aGameOpenWaitsForTheCharactersLayoutAndLandsAfterIt() =
        runTest {
            dao.openWindow(CHARACTER, "room")
            val windows = windows()

            windows.openFromGame("inventory")
            runCurrent()
            // The game has not named the character, so nothing can be known about its layout yet.
            assertEquals(emptyList(), windows.names())

            client.characterId.value = CHARACTER
            advanceUntilIdle()

            assertEquals(listOf("room", "inventory"), windows.names())
            assertTrue(dao.isOpen("inventory"))
        }

    @Test
    fun aGameCloseOvertakesTheOpenParkedBeforeIt() =
        runTest {
            val windows = windows()

            windows.openFromGame("loot")
            windows.closeFromGame("loot")
            runCurrent()
            client.characterId.value = CHARACTER
            advanceUntilIdle()

            assertEquals(emptyList(), windows.names())
        }

    @Test
    fun aWindowTheUserClosedStaysClosedWhenTheGameOpensIt() =
        runTest {
            val windows = windows()
            client.characterId.value = CHARACTER
            advanceUntilIdle()

            windows.open("spells")
            advanceUntilIdle()
            windows.close("spells")
            advanceUntilIdle()
            windows.openFromGame("spells")
            advanceUntilIdle()

            assertEquals(emptyList(), windows.names())
            assertTrue(repository.isHidden(CHARACTER, "spells"))
        }

    @Test
    fun aWindowTheGameClosedComesBackWhenTheGameOpensIt() =
        runTest {
            val windows = windows()
            client.characterId.value = CHARACTER
            advanceUntilIdle()

            windows.open("spells")
            advanceUntilIdle()
            windows.closeFromGame("spells")
            advanceUntilIdle()
            assertFalse(repository.isHidden(CHARACTER, "spells"))

            windows.openFromGame("spells")
            advanceUntilIdle()

            assertEquals(listOf("spells"), windows.names())
        }

    @Test
    fun theRestoreDoesNotAddASecondCopyOfAnOpenWindow() =
        runTest {
            dao.openWindow(CHARACTER, "room")
            val windows = windows()

            windows.open("room")
            client.characterId.value = CHARACTER
            advanceUntilIdle()

            assertEquals(listOf("room"), windows.names())
        }

    @Test
    fun aResidentPanelsCloseButtonRunsItsCommandButLeavesItOpen() =
        runTest {
            val windows = windows()
            client.windowInfo.value = listOf(panel("spells", resident = true))
            client.characterId.value = CHARACTER
            advanceUntilIdle()
            windows.open("spells")
            advanceUntilIdle()

            windows.closeFromPanelButton(WarlockAction.ClosePanel(id = "spells", command = "close spells", echo = null))
            advanceUntilIdle()

            assertEquals(listOf("close spells"), client.widgetCommands)
            assertEquals(listOf("spells"), windows.names())
            assertEquals(listOf("Cannot close a resident dialog (spells)"), client.debugLines)
        }

    @Test
    fun aTransientPanelCloseButtonClosesItAndSavesNothing() =
        runTest {
            val windows = windows()
            client.windowInfo.value = listOf(panel("loot", resident = false))
            client.characterId.value = CHARACTER
            advanceUntilIdle()
            windows.open("loot")
            advanceUntilIdle()

            windows.closeFromPanelButton(WarlockAction.ClosePanel(id = "loot", command = null, echo = null))
            advanceUntilIdle()

            assertEquals(emptyList(), windows.names())
            assertEquals(null, dao.rows.value[CHARACTER to "loot"])
            assertFalse(repository.isHidden(CHARACTER, "loot"))
        }

    private companion object {
        const val CHARACTER = "dr:grhim"
    }
}
