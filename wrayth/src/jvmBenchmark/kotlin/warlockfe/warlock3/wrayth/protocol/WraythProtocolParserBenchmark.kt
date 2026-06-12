package warlockfe.warlock3.wrayth.protocol

import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.BenchmarkMode
import kotlinx.benchmark.BenchmarkTimeUnit
import kotlinx.benchmark.Blackhole
import kotlinx.benchmark.Mode
import kotlinx.benchmark.OutputTimeUnit
import kotlinx.benchmark.Scope
import kotlinx.benchmark.State

/**
 * Microbenchmarks for [WraythProtocolHandler.parseLine] - the full path that turns one line of SGE
 * protocol into a list of [WraythEvent] (lex + parse + visit + element handling). Measured across
 * representative line shapes on a single long-lived handler, as in production.
 *
 * Run with: `./gradlew :wrayth:jvmBenchmarkBenchmark` (or `:wrayth:benchmark` for all targets).
 *
 * Throughput is reported in operations per millisecond - small, readable numbers, vs. the
 * ~10^5-10^6 ops/second the default unit would print.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(BenchmarkTimeUnit.MILLISECONDS)
class WraythProtocolParserBenchmark {
    // One long-lived handler, reused across calls like the real connection read loop does.
    private val handler = WraythProtocolHandler()

    // Plain narrative text with no tags - the common case, exercises the TEXT lexer rule.
    private val plainText = "You see a tall orc warrior standing guard by the gate, gripping a rusty halberd."

    // A combat line with styling and a clickable noun - a typical mix of tags, attributes, and text.
    private val styledLine =
        "<pushBold/>You attack the <a exist=\"42\" noun=\"orc\">orc</a> and <preset id=\"speech\">it staggers back</preset>!"

    // Stream routing plus a component update - nested empty/start/end tags.
    private val streamLine =
        "<pushStream id=\"inv\"/>a leather backpack<popStream/><component id=\"room desc\">A wide cobbled plaza.</component>"

    // A room line dense with command links, including angle brackets inside an attribute value.
    private val linkHeavyLine =
        "<style id=\"roomName\"/>[Town Square Central]<style id=\"\"/> Obvious exits: " +
            "<a cmd=\"go north\">north</a>, <a cmd=\"go east\">east</a>, <d cmd=\"go <hidden path>\">a hidden path</d>."

    // Text peppered with entity and character references - exercises the reference rules and unescaping.
    private val entityHeavyLine =
        "Roundtime: 3 sec. &lt;A &amp; B&gt; deal &#62;50 damage &#x26; gain &lt;xp&gt; for the &quot;kill&quot;."

    @Benchmark
    fun parsePlainText(bh: Blackhole) = bh.consume(handler.parseLine(plainText))

    @Benchmark
    fun parseStyledLine(bh: Blackhole) = bh.consume(handler.parseLine(styledLine))

    @Benchmark
    fun parseStreamLine(bh: Blackhole) = bh.consume(handler.parseLine(streamLine))

    @Benchmark
    fun parseLinkHeavyLine(bh: Blackhole) = bh.consume(handler.parseLine(linkHeavyLine))

    @Benchmark
    fun parseEntityHeavyLine(bh: Blackhole) = bh.consume(handler.parseLine(entityHeavyLine))
}
