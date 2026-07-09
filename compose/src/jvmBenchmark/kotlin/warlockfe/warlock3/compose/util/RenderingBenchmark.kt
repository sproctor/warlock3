package warlockfe.warlock3.compose.util

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.BenchmarkMode
import kotlinx.benchmark.BenchmarkTimeUnit
import kotlinx.benchmark.Blackhole
import kotlinx.benchmark.Mode
import kotlinx.benchmark.OutputTimeUnit
import kotlinx.benchmark.Scope
import kotlinx.benchmark.State
import warlockfe.warlock3.compose.model.LiteralHighlight
import warlockfe.warlock3.compose.model.ViewHighlight
import warlockfe.warlock3.core.client.WarlockAction
import warlockfe.warlock3.core.text.StyleDefinition
import warlockfe.warlock3.core.text.StyleLayer
import warlockfe.warlock3.core.text.StyledString
import warlockfe.warlock3.core.text.WarlockColor
import warlockfe.warlock3.core.text.WarlockStyle
import warlockfe.warlock3.core.text.toLayer

/**
 * Microbenchmarks for the per-line text-rendering work the game window does for every line: building
 * the Compose [AnnotatedString] from the game's StyledString model, applying the user's highlights,
 * and laying the text out with a [TextMeasurer]. This is the CPU cost incurred each time a line is
 * (re)composed and measured.
 *
 * Not covered: Compose recomposition/layout-node diffing itself, which needs an on-device
 * (Android instrumented) macrobenchmark rather than a JVM microbenchmark.
 *
 * Run with: `./gradlew :compose:jvmBenchmarkBenchmark`. Throughput is in operations per millisecond.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(BenchmarkTimeUnit.MILLISECONDS)
class RenderingBenchmark {
    private val noopAction: (WarlockAction) -> Unit = { _ -> }

    private val styleMap: Map<String, StyleLayer> =
        mapOf(
            "roomName" to StyleDefinition(textColor = WarlockColor(red = 255, green = 215, blue = 0), bold = true),
            "creature" to StyleDefinition(textColor = WarlockColor(red = 255, green = 90, blue = 90)),
            "object" to StyleDefinition(textColor = WarlockColor(red = 90, green = 170, blue = 255)),
            "speech" to StyleDefinition(textColor = WarlockColor(red = 120, green = 255, blue = 120)),
            "link" to StyleDefinition(textColor = WarlockColor(red = 120, green = 180, blue = 255), underline = true),
        ).mapValues { (_, style) -> style.toLayer() }

    // A representative room/combat line with several styled spans, including Capitalized proper names
    // (as real "Also here:" lines have) so the case-sensitive name-highlight match path is exercised.
    private val styledLine: StyledString =
        StyledString("You also see ") +
            StyledString("a greater orc", WarlockStyle("creature")) +
            StyledString(", ") +
            StyledString("a tattered scroll", WarlockStyle("object")) +
            StyledString(", and ") +
            StyledString("a wooden chest", WarlockStyle("object")) +
            StyledString(". Obvious exits: north, east, down. Also here: Zarnok, Vexil, Quorth.")

    // A realistic set of user highlights: a stack of literal names plus a couple of regexes.
    private val highlights: List<ViewHighlight> = buildHighlights()

    // Indexed once, as production does per highlight-list change, so the per-line benchmarks measure the
    // steady-state matching cost (index reuse) rather than rebuilding the index on every line.
    private val highlightIndex: HighlightIndex = HighlightIndex(highlights)

    // Pre-built so applyHighlights / layout measure only their own step.
    private val annotated: AnnotatedString = styledLine.toAnnotatedString(emptyMap(), styleMap, noopAction)

    // Lazy so a headless font/skiko problem (if any) only fails the layout benchmarks, not the rest.
    private val measurer: TextMeasurer by lazy {
        TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr)
    }

    @Benchmark
    fun convertStyledStringToAnnotated(bh: Blackhole) = bh.consume(styledLine.toAnnotatedString(emptyMap(), styleMap, noopAction))

    @Benchmark
    fun applyHighlights(bh: Blackhole) = bh.consume(annotated.highlight(highlightIndex))

    @Benchmark
    fun buildHighlightIndex(bh: Blackhole) = bh.consume(HighlightIndex(highlights))

    @Benchmark
    fun convertAndHighlight(bh: Blackhole) =
        bh.consume(styledLine.toAnnotatedString(emptyMap(), styleMap, noopAction).highlight(highlightIndex))

    // skipCache = true so this measures actual layout work, not a TextMeasurer cache hit (each new
    // game line is fresh text, i.e. a cache miss, so this reflects the real per-new-line layout cost).
    @Benchmark
    fun layoutAnnotated(bh: Blackhole) =
        bh.consume(measurer.measure(annotated, constraints = Constraints(maxWidth = 1000), skipCache = true))

    @Benchmark
    fun fullLinePipeline(bh: Blackhole) {
        val text = styledLine.toAnnotatedString(emptyMap(), styleMap, noopAction).highlight(highlightIndex).text
        bh.consume(measurer.measure(text, constraints = Constraints(maxWidth = 1000), skipCache = true))
    }

    private fun buildHighlights(): List<ViewHighlight> {
        // Model the real-world profile measured in the app: ~975 highlights, ALL literal (zero regex),
        // overwhelmingly single-word, whole-word, CASE-SENSITIVE proper names that are Capitalized
        // (player/creature names). Capitalization matters: the per-line pre-filter works in lowercase,
        // so lowercasing a Capitalized needle allocates a fresh string every time — which is precisely
        // the per-line garbage that precomputing the lowercased form removes. An all-lowercase set would
        // hide that cost, because String.lowercase() returns the same instance when nothing changes.
        val realNames = listOf("Zarnok", "Vexil", "Quorth", "Kobold", "Goblin", "Troll", "Orc", "Drake")
        val names = realNames + (0 until 965).map { "Hlword$it" }
        val literal =
            names.map { name ->
                LiteralHighlight(
                    literal = name,
                    matchPartialWord = false,
                    ignoreCase = false,
                    style = StyleDefinition(bold = true, textColor = WarlockColor(red = 255, green = 200, blue = 0)),
                    sound = null,
                )
            }
        // A couple of partial-word literals, as real highlight lists tend to have.
        val partialWord =
            listOf("stone", "wood").map { fragment ->
                LiteralHighlight(
                    literal = fragment,
                    matchPartialWord = true,
                    ignoreCase = true,
                    style = StyleDefinition(textColor = WarlockColor(red = 120, green = 200, blue = 255)),
                    sound = null,
                )
            }
        return literal + partialWord
    }
}
