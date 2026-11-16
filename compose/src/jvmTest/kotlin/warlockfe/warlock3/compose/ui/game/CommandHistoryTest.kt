package warlockfe.warlock3.compose.ui.game

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.SystemFileSystem
import warlockfe.warlock3.compose.deleteTestDir
import warlockfe.warlock3.core.prefs.config.CharacterConfigStore
import warlockfe.warlock3.core.prefs.repositories.ClientSettingRepository
import warlockfe.warlock3.core.prefs.repositories.CommandHistoryRepository
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CommandHistoryTest {
    private lateinit var dir: java.nio.file.Path

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("command-history-test")
    }

    @AfterTest
    fun tearDown() {
        historyJob.cancel()
        deleteTestDir(dir)
    }

    private val entry = TextFieldState()
    private val characterId = MutableStateFlow<String?>(null)

    // The histories' own coroutines, cancelled after each test: they collect settings forever.
    private val historyJob = Job()

    private fun TestScope.history(historySize: Flow<Int> = flowOf(ClientSettingRepository.DEFAULT_HISTORY_SIZE)) =
        CommandHistory(
            entry = entry,
            characterId = characterId,
            commandHistoryRepository =
                CommandHistoryRepository(
                    CharacterConfigStore(dir.toString(), SystemFileSystem),
                    SystemFileSystem,
                    StandardTestDispatcher(testScheduler),
                ),
            minCommandLength = flowOf(ClientSettingRepository.DEFAULT_MIN_COMMAND_LENGTH),
            historySize = historySize,
            scope = CoroutineScope(StandardTestDispatcher(testScheduler) + historyJob),
        )

    @Test
    fun stepsBackAndForwardThroughSentCommands() =
        runTest {
            val history = history()
            advanceUntilIdle()
            history.record("look")
            history.record("north")
            entry.setTextAndPlaceCursorAtEnd("half typed")

            history.previous()
            assertEquals("north", entry.text.toString())
            history.previous()
            assertEquals("look", entry.text.toString())
            // Nothing older.
            history.previous()
            assertEquals("look", entry.text.toString())

            history.next()
            history.next()
            // Back to what was being typed.
            assertEquals("half typed", entry.text.toString())
        }

    @Test
    fun keepsNeitherShortCommandsNorARepeatOfTheLast() =
        runTest {
            val history = history()
            advanceUntilIdle()
            history.record("look")
            history.record("n")
            history.record("look")

            assertEquals("look", history.commandAt(1))
            assertEquals(null, history.commandAt(2))
        }

    @Test
    fun savesEachCharactersHistoryAndLoadsItOnConnect() =
        runTest {
            characterId.value = "char"
            val first = history()
            advanceUntilIdle()
            first.record("look")
            first.record("north")
            advanceUntilIdle()

            val second = history()
            advanceUntilIdle()

            assertEquals("north", second.commandAt(1))
            assertEquals("look", second.commandAt(2))
        }

    @Test
    fun searchesBackForTheTypedQueryAndSendsTheMatch() =
        runTest {
            val history = history()
            advanceUntilIdle()
            history.record("attack orc")
            history.record("look")
            history.record("attack troll")

            history.searchBack()
            assertTrue(history.searching)
            entry.setTextAndPlaceCursorAtEnd("att")
            Snapshot.sendApplyNotifications()
            advanceUntilIdle()
            assertEquals("attack troll", history.search.value?.match)

            // Again steps to the next older match.
            history.searchBack()
            assertEquals("attack orc", history.search.value?.match)

            assertEquals("attack orc", history.acceptSearch())
            assertFalse(history.searching)
            assertEquals("", entry.text.toString())
            // Sending it makes it the newest command.
            assertEquals("attack orc", history.commandAt(1))
        }

    @Test
    fun leavingASearchKeepsTheMatchForEditing() =
        runTest {
            val history = history()
            advanceUntilIdle()
            history.record("attack orc")

            history.searchBack()
            entry.setTextAndPlaceCursorAtEnd("orc")
            Snapshot.sendApplyNotifications()
            advanceUntilIdle()
            history.exitSearch()

            assertNull(history.search.value)
            assertEquals("attack orc", entry.text.toString())
        }

    @Test
    fun shrinkingTheHistoryPastTheShownCommandKeepsItAsTheEntry() =
        runTest {
            val historySize = MutableStateFlow(ClientSettingRepository.DEFAULT_HISTORY_SIZE)
            val history = history(historySize)
            advanceUntilIdle()
            history.record("look")
            history.record("north")
            history.record("south")
            history.previous()
            history.previous()
            history.previous()
            assertEquals("look", entry.text.toString())

            historySize.value = 1
            advanceUntilIdle()

            // What was shown stays in the entry, and stepping back again starts from the newest.
            assertEquals("look", entry.text.toString())
            history.previous()
            assertEquals("south", entry.text.toString())
            history.next()
            assertEquals("look", entry.text.toString())
        }

    @Test
    fun flushSavesACommandWhoseLaunchedSaveNeverRan() =
        runTest {
            characterId.value = "char"
            val first = history()
            advanceUntilIdle()
            first.record("look")
            // The game closes before the save launched for "look" is dispatched.
            historyJob.cancel()
            first.flush()

            val secondJob = Job()
            val second =
                CommandHistory(
                    entry = TextFieldState(),
                    characterId = characterId,
                    commandHistoryRepository =
                        CommandHistoryRepository(
                            CharacterConfigStore(dir.toString(), SystemFileSystem),
                            SystemFileSystem,
                            StandardTestDispatcher(testScheduler),
                        ),
                    minCommandLength = flowOf(ClientSettingRepository.DEFAULT_MIN_COMMAND_LENGTH),
                    historySize = flowOf(ClientSettingRepository.DEFAULT_HISTORY_SIZE),
                    scope = CoroutineScope(StandardTestDispatcher(testScheduler) + secondJob),
                )
            advanceUntilIdle()
            secondJob.cancel()
            assertEquals("look", second.commandAt(1))
        }

    @Test
    fun aNegativeHistorySizeKeepsNothingButStillSends() =
        runTest {
            val history = history(flowOf(-2))
            advanceUntilIdle()

            history.record("look")

            assertEquals("", history.commandAt(0))
            assertEquals(null, history.commandAt(1))
        }
}
