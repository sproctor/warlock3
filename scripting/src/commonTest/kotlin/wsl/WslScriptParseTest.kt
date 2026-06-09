package warlockfe.warlock3.scripting.wsl

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlinx.io.writeString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WslScriptParseTest {
    private fun parse(
        name: String,
        content: String,
    ): List<WslLine> {
        val path = Path(SystemTemporaryDirectory, "wsltest-$name.wsl")
        SystemFileSystem.sink(path).buffered().use { it.writeString(content) }
        try {
            return WslScript(name, path, SystemFileSystem).parse()
        } finally {
            SystemFileSystem.delete(path)
        }
    }

    /** Joins the literal text portions of a command, mirroring how it would be assembled. */
    private fun WslStatement.commandText(): String {
        val command = this as WslStatement.WslCommand
        return command.contents
            .filterIsInstance<WslCommandContent.Text>()
            .joinToString("") { it.text }
    }

    @Test
    fun parsesSingleCommand() {
        val lines = parse("single", "echo hello")
        assertEquals(1, lines.size)
        assertTrue(lines[0].labels.isEmpty())
        assertEquals("echo hello", lines[0].statement.commandText())
    }

    @Test
    fun parsesMultipleLines() {
        val lines = parse("multi", "echo one\necho two\necho three")
        assertEquals(3, lines.size)
        assertEquals("echo one", lines[0].statement.commandText())
        assertEquals("echo two", lines[1].statement.commandText())
        assertEquals("echo three", lines[2].statement.commandText())
    }

    @Test
    fun parsesLabel() {
        val lines = parse("label", "start: echo hi")
        assertEquals(1, lines.size)
        assertEquals(listOf("start"), lines[0].labels)
        assertEquals("echo hi", lines[0].statement.commandText())
    }

    @Test
    fun parsesMultipleLabels() {
        val lines = parse("labels", "a: b: echo hi")
        assertEquals(listOf("a", "b"), lines[0].labels)
    }

    @Test
    fun parsesComment() {
        val lines = parse("comment", "# this is a comment\necho hi")
        assertEquals(2, lines.size)
        // The comment line parses to an empty command
        assertEquals("", lines[0].statement.commandText())
        assertEquals("echo hi", lines[1].statement.commandText())
    }

    @Test
    fun parsesIfThen() {
        val lines = parse("ifthen", "if 1 = 1 then echo yes")
        assertEquals(1, lines.size)
        val statement = lines[0].statement
        assertTrue(statement is WslStatement.ConditionalStatement)
        assertEquals("echo yes", (statement.ifCommand as WslStatement).commandText())
        assertNull(statement.elseCommand)
    }

    @Test
    fun parsesIfThenElse() {
        val lines = parse("ifelse", "if 1 = 1 then echo yes\nelse echo no")
        assertEquals(1, lines.size)
        val statement = lines[0].statement
        assertTrue(statement is WslStatement.ConditionalStatement)
        assertEquals("echo yes", (statement.ifCommand as WslStatement).commandText())
        assertNotNull(statement.elseCommand)
        assertEquals("echo no", (statement.elseCommand as WslStatement).commandText())
    }

    @Test
    fun parsesVariableInCommand() {
        val lines = parse("var", "echo %foo%")
        val command = lines[0].statement as WslStatement.WslCommand
        val variable = command.contents.filterIsInstance<WslCommandContent.Variable>().single()
        assertTrue(variable.name.startsWith("foo"))
    }

    @Test
    fun parsesDoublePercent() {
        val lines = parse("doublepercent", "echo 50%%")
        // %% escapes to a literal %
        assertEquals("echo 50%", lines[0].statement.commandText())
    }

    @Test
    fun parsesExpressionInCommand() {
        val lines = parse("expr", "echo %{1 + 2}")
        val command = lines[0].statement as WslStatement.WslCommand
        assertEquals(1, command.contents.filterIsInstance<WslCommandContent.Expression>().size)
    }

    @Test
    fun emptyExpressionThrows() {
        assertFailsWith<WslParseException> {
            parse("emptyexpr", "echo %{}")
        }
    }
}
