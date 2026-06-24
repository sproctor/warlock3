package warlockfe.warlock3.scripting.wsl

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlinx.io.writeString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * End-to-end coverage of expression parsing and evaluation: each test parses a `%{...}`
 * expression out of a command line and evaluates it against a [WslContext].
 */
class WslExpressionTest {
    private fun parseExpression(content: String): WslExpression {
        val path = Path(SystemTemporaryDirectory, "wslexpr.wsl")
        SystemFileSystem.sink(path).buffered().use { it.writeString("echo %{$content}") }
        try {
            val lines = WslScript("expr", path, SystemFileSystem).parse()
            val command = lines[0].statement as WslStatement.WslCommand
            return command.contents
                .filterIsInstance<WslCommandContent.Expression>()
                .single()
                .expression
        } finally {
            SystemFileSystem.delete(path)
        }
    }

    private suspend fun CoroutineScope.eval(
        content: String,
        configure: WslContext.() -> Unit = {},
    ): WslValue {
        val context = buildTestContext(this)
        context.configure()
        return parseExpression(content).getValue(context)
    }

    // --- arithmetic ---

    @Test
    fun addsNumbers() =
        runTest {
            assertEquals("3", backgroundScope.eval("1 + 2").toString())
        }

    @Test
    fun subtractsNumbers() =
        runTest {
            assertEquals("2", backgroundScope.eval("5 - 3").toString())
        }

    @Test
    fun multipliesNumbers() =
        runTest {
            assertEquals("6", backgroundScope.eval("2 * 3").toString())
        }

    @Test
    fun dividesNumbers() =
        runTest {
            assertEquals("2.5", backgroundScope.eval("10 / 4").toString())
        }

    @Test
    fun multiplicationHasHigherPrecedenceThanAddition() =
        runTest {
            assertEquals("14", backgroundScope.eval("2 + 3 * 4").toString())
        }

    @Test
    fun parenthesesOverridePrecedence() =
        runTest {
            assertEquals("20", backgroundScope.eval("(2 + 3) * 4").toString())
        }

    @Test
    fun divideByZeroThrows() =
        runTest {
            assertFailsWith<WslRuntimeException> {
                backgroundScope.eval("1 / 0")
            }
        }

    // --- strings ---

    @Test
    fun concatenatesStrings() =
        runTest {
            assertEquals("ab", backgroundScope.eval("\"a\" + \"b\"").toString())
        }

    @Test
    fun concatenatesStringAndNumber() =
        runTest {
            assertEquals("a1", backgroundScope.eval("\"a\" + 1").toString())
        }

    @Test
    fun repeatsStringByNumber() =
        runTest {
            assertEquals("ababab", backgroundScope.eval("\"ab\" * 3").toString())
        }

    @Test
    fun multiplyByNonNumericStringThrows() =
        runTest {
            assertFailsWith<WslRuntimeException> {
                backgroundScope.eval("\"ab\" * \"cd\"")
            }
        }

    // --- comparison ---

    @Test
    fun greaterThan() =
        runTest {
            assertTrue(backgroundScope.eval("2 > 1").toBoolean())
            assertFalse(backgroundScope.eval("1 > 2").toBoolean())
        }

    @Test
    fun greaterThanOrEqual() =
        runTest {
            assertTrue(backgroundScope.eval("2 >= 2").toBoolean())
            assertFalse(backgroundScope.eval("1 >= 2").toBoolean())
        }

    @Test
    fun lessThan() =
        runTest {
            assertTrue(backgroundScope.eval("1 < 2").toBoolean())
            assertFalse(backgroundScope.eval("2 < 1").toBoolean())
        }

    @Test
    fun stringComparisonIsLexicographic() =
        runTest {
            assertTrue(backgroundScope.eval("\"b\" > \"a\"").toBoolean())
        }

    // --- equality ---

    @Test
    fun numericEquality() =
        runTest {
            assertTrue(backgroundScope.eval("1 = 1").toBoolean())
            assertFalse(backgroundScope.eval("1 = 2").toBoolean())
        }

    @Test
    fun inequality() =
        runTest {
            assertTrue(backgroundScope.eval("1 != 2").toBoolean())
            assertFalse(backgroundScope.eval("1 != 1").toBoolean())
        }

    @Test
    fun stringEqualityIsCaseInsensitive() =
        runTest {
            assertTrue(backgroundScope.eval("\"Hello\" = \"hello\"").toBoolean())
        }

    // --- logical ---

    @Test
    fun logicalAnd() =
        runTest {
            assertTrue(backgroundScope.eval("true and true").toBoolean())
            assertFalse(backgroundScope.eval("true and false").toBoolean())
        }

    @Test
    fun logicalOr() =
        runTest {
            assertTrue(backgroundScope.eval("false or true").toBoolean())
            assertFalse(backgroundScope.eval("false or false").toBoolean())
        }

    @Test
    fun logicalNot() =
        runTest {
            assertFalse(backgroundScope.eval("not true").toBoolean())
            assertTrue(backgroundScope.eval("not false").toBoolean())
        }

    @Test
    fun chainedComparisonsWithAnd() =
        runTest {
            assertTrue(backgroundScope.eval("1 < 2 and 3 > 2").toBoolean())
        }

    // --- infix contains ---

    @Test
    fun containsSubstring() =
        runTest {
            assertTrue(backgroundScope.eval("\"hello\" contains \"ell\"").toBoolean())
            assertFalse(backgroundScope.eval("\"hello\" contains \"xyz\"").toBoolean())
        }

    @Test
    fun containsRegex() =
        runTest {
            assertTrue(backgroundScope.eval("\"hello\" containsre \"h.l\"").toBoolean())
            assertFalse(backgroundScope.eval("\"hello\" containsre \"^z\"").toBoolean())
        }

    // --- prefix exists ---

    @Test
    fun existsIsTrueForSetVariable() =
        runTest {
            val result =
                backgroundScope.eval("exists foo") {
                    setScriptVariableRaw("foo", WslString("bar"))
                }
            assertTrue(result.toBoolean())
        }

    @Test
    fun existsIsFalseForUnsetVariable() =
        runTest {
            assertFalse(backgroundScope.eval("exists missing").toBoolean())
        }

    // --- variables and indexing ---

    @Test
    fun variableReferenceInExpression() =
        runTest {
            val result =
                backgroundScope.eval("x + 1") {
                    setScriptVariableRaw(
                        "x",
                        WslNumber(
                            com.ionspin.kotlin.bignum.decimal.BigDecimal
                                .fromInt(5),
                        ),
                    )
                }
            assertEquals("6", result.toString())
        }

    @Test
    fun mapIndexingInExpression() =
        runTest {
            val result =
                backgroundScope.eval("m[\"k\"]") {
                    setScriptVariableRaw("m", WslMap(mapOf("k" to WslString("v"))))
                }
            assertEquals("v", result.toString())
        }

    @Test
    fun unknownVariableIsNull() =
        runTest {
            assertTrue(backgroundScope.eval("missing").isNull())
        }
}
