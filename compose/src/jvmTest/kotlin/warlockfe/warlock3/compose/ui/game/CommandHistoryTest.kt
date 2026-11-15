package warlockfe.warlock3.compose.ui.game

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import warlockfe.warlock3.compose.AppContainer
import warlockfe.warlock3.compose.deleteTestDir
import warlockfe.warlock3.compose.testAppContainer
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

    private fun TestScope.settle() = advanceUntilIdle()

    private suspend fun TestScope.container(): AppContainer = testAppContainer(dir, StandardTestDispatcher(testScheduler))

    private fun TestScope.history(container: AppContainer) =
        CommandHistory(
            entry,
            characterId,
            container.commandHistoryRepository,
            container.clientSettings,
            CoroutineScope(StandardTestDispatcher(testScheduler) + historyJob),
        )

    @Test
    fun stepsBackAndForwardThroughSentCommands() =
        runTest {
            val history = history(container())
            settle()
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
            val history = history(container())
            settle()
            history.record("look")
            history.record("n")
            history.record("look")

            assertEquals("look", history.commandAt(1))
            assertEquals(null, history.commandAt(2))
        }

    @Test
    fun savesEachCharactersHistoryAndLoadsItOnConnect() =
        runTest {
            val container = container()
            characterId.value = "char"
            val first = history(container)
            settle()
            first.record("look")
            first.record("north")
            settle()

            val second = history(container)
            settle()

            assertEquals("north", second.commandAt(1))
            assertEquals("look", second.commandAt(2))
        }

    @Test
    fun searchesBackForTheTypedQueryAndSendsTheMatch() =
        runTest {
            val history = history(container())
            settle()
            history.record("attack orc")
            history.record("look")
            history.record("attack troll")

            history.searchBack()
            assertTrue(history.searching)
            entry.setTextAndPlaceCursorAtEnd("att")
            Snapshot.sendApplyNotifications()
            settle()
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
            val history = history(container())
            settle()
            history.record("attack orc")

            history.searchBack()
            entry.setTextAndPlaceCursorAtEnd("orc")
            Snapshot.sendApplyNotifications()
            settle()
            history.exitSearch()

            assertNull(history.search.value)
            assertEquals("attack orc", entry.text.toString())
        }
}
