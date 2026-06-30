package warlockfe.warlock3.scripting.wsl

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * End-to-end coverage of whole scripts: each test parses a small example script, runs it through
 * the interpreter the way [WslScriptInstance] would, and asserts on the values it computes (script
 * variables and echoed output).
 */
class WslScriptTest {
    private suspend fun CoroutineScope.runScript(
        script: String,
        client: FakeWarlockClient = FakeWarlockClient(),
    ): WslContext {
        val context = buildTestContext(this, lines = parseScript(script), client = client)
        context.runToCompletion()
        return context
    }

    // --- arithmetic via the counter command ---

    @Test
    fun counterArithmeticChainsCorrectly() =
        runTest {
            // (10 + 5) * 2 - 6 = 24
            val ctx =
                backgroundScope.runScript(
                    """
                    counter set 10
                    counter add 5
                    counter multiply 2
                    counter subtract 6
                    """.trimIndent(),
                )
            assertEquals("24", ctx.lookupVariable("c")?.toText())
        }

    @Test
    fun divisionProducesDecimalResult() =
        runTest {
            val ctx = backgroundScope.runScript("var half %{10 / 4}")
            assertEquals("2.5", ctx.lookupVariable("half")?.toText())
        }

    // --- expression evaluation into variables ---

    @Test
    fun expressionRespectsOperatorPrecedence() =
        runTest {
            val ctx = backgroundScope.runScript("var result %{2 + 3 * 4}")
            assertEquals("14", ctx.lookupVariable("result")?.toText())
        }

    @Test
    fun parenthesesOverridePrecedence() =
        runTest {
            val ctx = backgroundScope.runScript("var result %{(2 + 3) * 4}")
            assertEquals("20", ctx.lookupVariable("result")?.toText())
        }

    @Test
    fun booleanLogicEvaluates() =
        runTest {
            val ctx = backgroundScope.runScript("var ok %{2 > 1 and 3 >= 3}")
            assertEquals("true", ctx.lookupVariable("ok")?.toText())
        }

    // --- strings ---

    @Test
    fun stringConcatenationAndRepeat() =
        runTest {
            val ctx =
                backgroundScope.runScript(
                    """
                    var greeting %{"hello, " + "world"}
                    var bar %{"ab" * 3}
                    """.trimIndent(),
                )
            assertEquals("hello, world", ctx.lookupVariable("greeting")?.toText())
            assertEquals("ababab", ctx.lookupVariable("bar")?.toText())
        }

    // --- control flow ---

    @Test
    fun ifThenElseTakesTheTrueBranch() =
        runTest {
            val ctx =
                backgroundScope.runScript(
                    """
                    var n 7
                    if n > 5 then var size big
                    else var size small
                    """.trimIndent(),
                )
            assertEquals("big", ctx.lookupVariable("size")?.toText())
        }

    @Test
    fun ifThenElseTakesTheFalseBranch() =
        runTest {
            val ctx =
                backgroundScope.runScript(
                    """
                    var n 3
                    if n > 5 then var size big
                    else var size small
                    """.trimIndent(),
                )
            assertEquals("small", ctx.lookupVariable("size")?.toText())
        }

    @Test
    fun loopSumsWithGotoAndConditional() =
        runTest {
            // Sums 1 + 2 + 3 + 4 + 5 = 15
            val ctx =
                backgroundScope.runScript(
                    """
                    counter set 0
                    var i 1
                    sum: counter add %i
                    var i %{i + 1}
                    if i <= 5 then goto sum
                    """.trimIndent(),
                )
            assertEquals("15", ctx.lookupVariable("c")?.toText())
        }

    @Test
    fun exitStopsExecutionEarly() =
        runTest {
            val ctx =
                backgroundScope.runScript(
                    """
                    var a 1
                    exit
                    var a 2
                    """.trimIndent(),
                )
            // The line after `exit` never runs, so `a` keeps its first value.
            assertEquals("1", ctx.lookupVariable("a")?.toText())
        }

    // --- end-to-end output ---

    @Test
    fun echoOutputsComputedValue() =
        runTest {
            val client = FakeWarlockClient()
            backgroundScope.runScript(
                """
                counter set 3
                counter multiply 7
                echo result is %c
                """.trimIndent(),
                client = client,
            )
            assertEquals(listOf("result is 21"), client.printed.map { it.toText() })
        }
}
