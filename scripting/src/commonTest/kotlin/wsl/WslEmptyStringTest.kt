package warlockfe.warlock3.scripting.wsl

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlinx.io.writeString
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class WslEmptyStringTest {
    private fun parse(
        name: String,
        content: String,
    ): List<WslLine> {
        val path = Path(SystemTemporaryDirectory, "wslempty-$name.wsl")
        SystemFileSystem.sink(path).buffered().use { it.writeString(content) }
        try {
            return WslScript(name, path, SystemFileSystem).parse()
        } finally {
            SystemFileSystem.delete(path)
        }
    }

    private fun conditionOf(lines: List<WslLine>): WslExpression = (lines.single().statement as WslStatement.ConditionalStatement).condition

    @Test
    fun anEmptyStringLiteralIsAnEmptyString() {
        // `stringContent*` in the grammar takes zero or more, so "" parses to no content at all, and
        // evaluating it used to reduce an empty list. Reported from the field against 3.0.166.
        runTest {
            val context = buildTestContext(scope = backgroundScope)

            val value = conditionOf(parse("empty", """if "" then echo hi""")).getValue(context)

            assertEquals("", value.toString())
        }
    }

    @Test
    fun anEmptyStringConcatenatesWithText() {
        runTest {
            val context = buildTestContext(scope = backgroundScope)

            val value = conditionOf(parse("concat", """if "" + "abc" then echo hi""")).getValue(context)

            assertEquals("abc", value.toString())
        }
    }

    @Test
    fun aNonEmptyStringStillWorks() {
        runTest {
            val context = buildTestContext(scope = backgroundScope)

            val value = conditionOf(parse("plain", """if "hello there" then echo hi""")).getValue(context)

            assertEquals("hello there", value.toString())
        }
    }
}
