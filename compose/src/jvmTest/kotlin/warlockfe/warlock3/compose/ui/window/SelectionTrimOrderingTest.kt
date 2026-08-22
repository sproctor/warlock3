package warlockfe.warlock3.compose.ui.window

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The window's selection clear (see the selection effect in [WindowViewScaffold]) has to run while
 * the rows a trim is about to take are still composed: it asks whether any composed row's line has
 * left the buffer, and a row that has already been disposed has already removed itself from that
 * set. If the observation lands a frame late the check silently never fires, and the crash the
 * clear exists to prevent comes back.
 *
 * That ordering belongs to Compose, not to us, so it is pinned here against a real scene.
 * [ImageComposeScene.render] runs the same performFrame -> measureAndLayout -> draw sequence the
 * desktop host does, and a LazyColumn disposes the items a list change removed during measure.
 *
 * The shape below mirrors the scaffold rather than composing it - that would need a client, the
 * repositories behind it and a dozen composition locals: a StateFlow of lines written off the
 * compose thread, rows that register themselves while composed, and an effect reading that flow.
 * Reading a rememberUpdatedState mirror of the composed list instead - which is what the scaffold
 * did first - moves the observation behind the composition that writes the mirror, which is the
 * one that disposes the rows.
 */
class SelectionTrimOrderingTest {
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun aTrimIsObservedWhileTheRowsItTakesAreStillComposed() {
        // Confined to one thread like the AWT event thread the app composes on. The default here is
        // Dispatchers.Unconfined, which resumes the collector inline on whichever thread wrote the
        // flow - that would answer a question about the test rather than about the app.
        val composeThread = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "compose-test") }
        try {
            val lines = MutableStateFlow((0L until 40L).toList())
            val composed = mutableSetOf<Long>()
            val sawDoomedRows = mutableListOf<Boolean>()

            val scene =
                composeThread.call {
                    ImageComposeScene(
                        width = 300,
                        height = 400,
                        density = Density(1f),
                        coroutineContext = composeThread.asCoroutineDispatcher(),
                    )
                }
            try {
                composeThread.call {
                    scene.setContent {
                        val current by lines.collectAsState()
                        LaunchedEffect(Unit) {
                            lines
                                .map { it.firstOrNull() }
                                .distinctUntilChanged()
                                .drop(1)
                                .collect { oldest ->
                                    val cutoff = oldest ?: Long.MAX_VALUE
                                    sawDoomedRows += composed.any { it < cutoff }
                                }
                        }
                        LazyColumn(Modifier.fillMaxWidth()) {
                            items(count = current.size, key = { index -> current[index] }) { index ->
                                val serial = current[index]
                                DisposableEffect(serial) {
                                    composed += serial
                                    onDispose { composed -= serial }
                                }
                                Box(Modifier.fillMaxWidth().height(20.dp))
                            }
                        }
                    }
                    scene.render()
                    scene.render()
                }

                val rowsBefore = composeThread.call { composed.toSet() }
                assertTrue(0L in rowsBefore, "the front of the list should be composed to start with")

                // The trim, off the compose thread, as the stream's work queue does it.
                lines.value = lines.value.drop(10)
                composeThread.call { scene.render() }

                val observations = composeThread.call { sawDoomedRows.toList() }
                assertEquals(
                    listOf(true),
                    observations,
                    "the trim should be seen while the rows it drops are still composed",
                )
                assertTrue(
                    composeThread.call { composed.none { it < 10L } },
                    "and those rows should be gone by the time the frame is over",
                )
            } finally {
                composeThread.call { scene.close() }
            }
        } finally {
            composeThread.shutdown()
        }
    }

    private fun <T> ExecutorService.call(block: () -> T): T = submit(Callable(block)).get()
}
