package warlockfe.warlock3.wrayth.protocol

import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Blackhole
import kotlinx.benchmark.Scope
import kotlinx.benchmark.State
import org.antlr.v4.kotlinruntime.CharStreams
import org.antlr.v4.kotlinruntime.CommonTokenStream
import warlockfe.warlock3.wrayth.parsers.generated.WraythLexer
import warlockfe.warlock3.wrayth.parsers.generated.WraythParser

/**
 * Microbenchmarks for the Wrayth protocol parser: the lexer + parser + [WraythNodeVisitor] pipeline
 * that turns one line of SGE protocol into a list of [Content]. This is the pure parse step (no
 * protocol-handler state), measured across representative line shapes.
 *
 * Run with: `./gradlew :wrayth:jvmBenchmarkBenchmark` (or `:wrayth:benchmark` for all targets).
 */
@State(Scope.Benchmark)
class WraythProtocolParserBenchmark {
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
    fun parsePlainText(bh: Blackhole) = bh.consume(parse(plainText))

    @Benchmark
    fun parseStyledLine(bh: Blackhole) = bh.consume(parse(styledLine))

    @Benchmark
    fun parseStreamLine(bh: Blackhole) = bh.consume(parse(streamLine))

    @Benchmark
    fun parseLinkHeavyLine(bh: Blackhole) = bh.consume(parse(linkHeavyLine))

    @Benchmark
    fun parseEntityHeavyLine(bh: Blackhole) = bh.consume(parse(entityHeavyLine))

    private fun parse(line: String): List<Content> {
        val lexer = WraythLexer(CharStreams.fromString(line))
        val parser = WraythParser(CommonTokenStream(lexer))
        return WraythNodeVisitor.visitDocument(parser.document())
    }
}
