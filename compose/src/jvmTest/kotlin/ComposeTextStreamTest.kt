import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import warlockfe.warlock3.compose.model.LiteralHighlight
import warlockfe.warlock3.compose.model.ViewHighlight
import warlockfe.warlock3.compose.ui.window.ComposeTextStream
import warlockfe.warlock3.compose.ui.window.StreamImageLine
import warlockfe.warlock3.compose.ui.window.StreamLine
import warlockfe.warlock3.compose.ui.window.StreamTextLine
import warlockfe.warlock3.compose.ui.window.StreamWorkQueue
import warlockfe.warlock3.compose.util.HighlightIndex
import warlockfe.warlock3.core.text.StyleDefinition
import warlockfe.warlock3.core.text.StyledString
import warlockfe.warlock3.core.text.StyledStringLeaf
import warlockfe.warlock3.core.text.StyledStringSubstring
import warlockfe.warlock3.core.text.StyledStringVariable
import warlockfe.warlock3.core.util.SoundPlayer
import warlockfe.warlock3.wrayth.util.CompiledAlteration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for [ComposeTextStream]'s displayed-line buffer: ordering, eviction at capacity, the
 * prompt/name filters, partial-line accumulation, component updates, and image lines.
 *
 * The stream does its work on a [StreamWorkQueue] (a FIFO channel drained by a single worker on
 * Dispatchers.Default). For the methods that submit directly, [Fixture.drain] queues a barrier op
 * after them and awaits it, so when it returns the prior ops have run. The two non-suspending
 * setters dispatch their rebuild through scope.launch, so those tests wait on the [lines] flow
 * reaching the expected state instead.
 */
class ComposeTextStreamTest {
    private object SilentSoundPlayer : SoundPlayer {
        override suspend fun playSound(filename: String): String? = null
    }

    private class Fixture(
        maxLines: Int = 1000,
        suppressPrompts: Boolean = false,
        showImages: Boolean = true,
        names: List<ViewHighlight> = emptyList(),
    ) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private val workQueue = StreamWorkQueue(scope)

        val stream =
            ComposeTextStream(
                id = "main",
                maxLines = maxLines,
                markLinks = false,
                showImages = showImages,
                showTimestamps = false,
                suppressPrompts = suppressPrompts,
                highlights = MutableStateFlow(HighlightIndex(emptyList())),
                names = MutableStateFlow(names),
                alterations = MutableStateFlow(emptyList<CompiledAlteration>()),
                presets = MutableStateFlow(emptyMap<String, StyleDefinition>()),
                soundPlayer = SilentSoundPlayer,
                workQueue = workQueue,
                scope = scope,
            )

        // FIFO barrier: a no-op op queued after the prior direct submits; awaiting it means they ran.
        suspend fun drain() {
            val done = CompletableDeferred<Unit>()
            workQueue.submit { done.complete(Unit) }
            done.await()
        }

        suspend fun awaitLines(predicate: (List<StreamLine>) -> Boolean): List<StreamLine> =
            withTimeout(5_000) { stream.lines.first(predicate) }

        fun close() = scope.cancel()
    }

    private fun text(value: String): StyledString = StyledString(value)

    // A line that is some fixed text followed by a component reference, e.g. "hp: " + {hp}.
    private fun lineWithComponent(
        prefix: String,
        component: String,
    ): StyledString =
        StyledString(
            persistentListOf(
                StyledStringSubstring(prefix, persistentListOf()),
                StyledStringVariable(component, persistentListOf()),
            ),
        )

    private fun List<StreamLine>.texts(): List<String?> = map { (it as StreamTextLine).text?.text }

    private fun List<StreamLine>.serials(): List<Long> = map { it.serialNumber }

    private inline fun withFixture(
        maxLines: Int = 1000,
        suppressPrompts: Boolean = false,
        showImages: Boolean = true,
        names: List<ViewHighlight> = emptyList(),
        block: (Fixture) -> Unit,
    ) {
        val fixture = Fixture(maxLines, suppressPrompts, showImages, names)
        try {
            block(fixture)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun appendsLinesInSerialOrder() =
        runBlocking {
            withFixture { f ->
                f.stream.appendLine(text("a"), ignoreWhenBlank = false, showWhenClosed = null)
                f.stream.appendLine(text("b"), ignoreWhenBlank = false, showWhenClosed = null)
                f.stream.appendLine(text("c"), ignoreWhenBlank = false, showWhenClosed = null)
                f.drain()

                assertEquals(
                    listOf("a", "b", "c"),
                    f.stream.lines.value
                        .texts(),
                )
                assertEquals(
                    listOf(0L, 1L, 2L),
                    f.stream.lines.value
                        .serials(),
                )
            }
        }

    @Test
    fun evictsOldestLinesAtCapacity() =
        runBlocking {
            withFixture(maxLines = 3) { f ->
                repeat(5) { i ->
                    f.stream.appendLine(text("line$i"), ignoreWhenBlank = false, showWhenClosed = null)
                }
                f.drain()

                val lines = f.stream.lines.value
                assertEquals(3, lines.size)
                assertEquals(listOf("line2", "line3", "line4"), lines.texts())
                assertEquals(listOf(2L, 3L, 4L), lines.serials())
            }
        }

    // Exercises the displayBacking / displayStart / compactBacking machinery across many evictions.
    @Test
    fun evictionStaysCorrectOverManyLines() =
        runBlocking {
            withFixture(maxLines = 10) { f ->
                repeat(100) { i ->
                    f.stream.appendLine(text("l$i"), ignoreWhenBlank = false, showWhenClosed = null)
                }
                f.drain()

                val lines = f.stream.lines.value
                assertEquals(10, lines.size)
                assertEquals((90 until 100).map { "l$it" }, lines.texts())
                assertEquals((90L until 100L).toList(), lines.serials())
            }
        }

    @Test
    fun clearEmptiesTheBuffer() =
        runBlocking {
            withFixture { f ->
                f.stream.appendLine(text("a"), ignoreWhenBlank = false, showWhenClosed = null)
                f.drain()
                assertEquals(1, f.stream.lines.value.size)

                f.stream.clear()
                f.drain()
                assertTrue(
                    f.stream.lines.value
                        .isEmpty(),
                )
            }
        }

    @Test
    fun setMaxLinesTrimsExistingBuffer() =
        runBlocking {
            withFixture(maxLines = 100) { f ->
                repeat(10) { i ->
                    f.stream.appendLine(text("l$i"), ignoreWhenBlank = false, showWhenClosed = null)
                }
                f.drain()
                assertEquals(10, f.stream.lines.value.size)

                f.stream.setMaxLines(3)
                f.drain()

                // Shrinking the cap trims to exactly maxLines, keeping the most recent lines.
                assertEquals(
                    listOf("l7", "l8", "l9"),
                    f.stream.lines.value
                        .texts(),
                )

                // A following append stays at the cap rather than overshooting to maxLines + 1.
                f.stream.appendLine(text("l10"), ignoreWhenBlank = false, showWhenClosed = null)
                f.drain()
                assertEquals(
                    listOf("l8", "l9", "l10"),
                    f.stream.lines.value
                        .texts(),
                )
            }
        }

    @Test
    fun suppressPromptsFromConstructorHidesPromptLines() =
        runBlocking {
            withFixture(suppressPrompts = true) { f ->
                f.stream.appendLine(text("normal"), ignoreWhenBlank = false, showWhenClosed = null)
                f.stream.appendPartial(text(">"), isPrompt = true)
                f.drain()

                assertEquals(
                    listOf("normal"),
                    f.stream.lines.value
                        .texts(),
                )
            }
        }

    @Test
    fun togglingSuppressPromptsHidesThenShowsPrompt() =
        runBlocking {
            withFixture(suppressPrompts = false) { f ->
                f.stream.appendLine(text("normal"), ignoreWhenBlank = false, showWhenClosed = null)
                f.stream.appendPartial(text(">"), isPrompt = true)
                f.drain()
                assertEquals(
                    listOf("normal", ">"),
                    f.stream.lines.value
                        .texts(),
                )

                f.stream.setSuppressPrompts(true)
                assertEquals(listOf("normal"), f.awaitLines { it.size == 1 }.texts())

                f.stream.setSuppressPrompts(false)
                assertEquals(listOf("normal", ">"), f.awaitLines { it.size == 2 }.texts())
            }
        }

    @Test
    fun nameFilterShowsOnlyMatchingLines() =
        runBlocking {
            val orc = LiteralHighlight(literal = "orc", matchPartialWord = false, ignoreCase = true, style = null, sound = null)
            withFixture(names = listOf(orc)) { f ->
                f.stream.appendLine(text("an orc appears"), ignoreWhenBlank = false, showWhenClosed = null)
                f.stream.appendLine(text("nothing here"), ignoreWhenBlank = false, showWhenClosed = null)
                f.drain()
                assertEquals(2, f.stream.lines.value.size)

                f.stream.setNameFilter(true)
                assertEquals(listOf("an orc appears"), f.awaitLines { it.size == 1 }.texts())

                f.stream.setNameFilter(false)
                assertEquals(2, f.awaitLines { it.size == 2 }.size)
            }
        }

    @Test
    fun partialLineAccumulatesOnASingleSerial() =
        runBlocking {
            withFixture { f ->
                f.stream.appendPartial(text("hel"), isPrompt = false)
                f.stream.appendPartial(text("lo"), isPrompt = false)
                f.drain()
                assertEquals(
                    listOf("hello"),
                    f.stream.lines.value
                        .texts(),
                )
                assertEquals(
                    listOf(0L),
                    f.stream.lines.value
                        .serials(),
                )

                f.stream.appendPartialAndEol(text("!"))
                f.drain()
                assertEquals(
                    listOf("hello!"),
                    f.stream.lines.value
                        .texts(),
                )

                f.stream.appendLine(text("next"), ignoreWhenBlank = false, showWhenClosed = null)
                f.drain()
                assertEquals(
                    listOf("hello!", "next"),
                    f.stream.lines.value
                        .texts(),
                )
                assertEquals(
                    listOf(0L, 1L),
                    f.stream.lines.value
                        .serials(),
                )
            }
        }

    // A single component value change must re-render every line that references it.
    @Test
    fun updateComponentRefreshesAllOccurrences() =
        runBlocking {
            withFixture { f ->
                repeat(3) { i ->
                    f.stream.appendLine(lineWithComponent("row$i ", "hp"), ignoreWhenBlank = false, showWhenClosed = null)
                }
                f.drain()
                assertEquals(
                    listOf("row0 ", "row1 ", "row2 "),
                    f.stream.lines.value
                        .texts(),
                )

                f.stream.updateComponent("hp", text("100"))
                f.drain()
                assertEquals(
                    listOf("row0 100", "row1 100", "row2 100"),
                    f.stream.lines.value
                        .texts(),
                )
            }
        }

    @Test
    fun imageLinesRespectMaxLines() =
        runBlocking {
            withFixture(maxLines = 3) { f ->
                repeat(5) { i ->
                    f.stream.appendResource("img$i.png")
                }
                f.drain()

                val lines = f.stream.lines.value
                assertEquals(3, lines.size)
                assertEquals(listOf("img2.png", "img3.png", "img4.png"), lines.map { (it as StreamImageLine).url })
            }
        }

    @Test
    fun imageLinesIgnoredWhenImagesDisabled() =
        runBlocking {
            withFixture(showImages = false) { f ->
                f.stream.appendResource("img.png")
                f.drain()
                assertTrue(
                    f.stream.lines.value
                        .isEmpty(),
                )
            }
        }

    // Streaming updates to a suppressed prompt must not surface it or throw (the line is never in the
    // displayed list, so replaceLineInView keeps hitting the filtered-out path).
    @Test
    fun streamingSuppressedPromptNeverAppears() =
        runBlocking {
            withFixture(suppressPrompts = true) { f ->
                f.stream.appendLine(text("normal"), ignoreWhenBlank = false, showWhenClosed = null)
                f.stream.appendPartial(text(">"), isPrompt = true)
                f.stream.appendPartial(text(" ready"), isPrompt = true)
                f.stream.appendPartial(text(">"), isPrompt = true)
                f.drain()

                assertEquals(
                    listOf("normal"),
                    f.stream.lines.value
                        .texts(),
                )
            }
        }
}
