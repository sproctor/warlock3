package warlockfe.warlock3.scripting.wsl

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlinx.io.writeString
import warlockfe.warlock3.core.client.ClientTextEvent
import warlockfe.warlock3.core.prefs.models.VariableEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class WslContextTest {
    /** Writes [content] to a temp file, parses it into lines, then removes the file. */
    private fun script(name: String, content: String): Pair<Path, List<WslLine>> {
        val path = Path(SystemTemporaryDirectory, "wslctx-$name.wsl")
        SystemFileSystem.sink(path).buffered().use { it.writeString(content) }
        val lines = WslScript(name, path, SystemFileSystem).parse()
        SystemFileSystem.delete(path)
        return path to lines
    }

    private fun makeContext(
        scope: CoroutineScope,
        file: Path,
        lines: List<WslLine>,
        client: FakeWarlockClient = FakeWarlockClient(),
        variableDao: FakeVariableDao = FakeVariableDao(),
        scriptManager: FakeScriptManager = FakeScriptManager(),
    ): WslContext =
        buildTestContext(
            scope = scope,
            lines = lines,
            client = client,
            variableDao = variableDao,
            scriptManager = scriptManager,
            file = file,
        )

    @Test
    fun lookupUnknownVariableReturnsNull() = runTest {
        val (file, lines) = script("unknown", "echo hi")
        val ctx = makeContext(backgroundScope, file, lines)
        assertNull(ctx.lookupVariable("nope"))
    }

    @Test
    fun setAndLookupLocalVariable() = runTest {
        val (file, lines) = script("local", "echo hi")
        val ctx = makeContext(backgroundScope, file, lines)
        ctx.setLocalVariable("foo", WslString("bar"))
        assertEquals(WslString("bar"), ctx.lookupVariable("foo"))
    }

    @Test
    fun setAndLookupScriptVariable() = runTest {
        val (file, lines) = script("scriptvar", "echo hi")
        val ctx = makeContext(backgroundScope, file, lines)
        ctx.setScriptVariableRaw("foo", WslString("bar"))
        assertEquals(WslString("bar"), ctx.lookupVariable("foo"))
        assertTrue(ctx.hasScriptVariable("foo"))
    }

    @Test
    fun localVariableShadowsScriptVariable() = runTest {
        val (file, lines) = script("shadow", "echo hi")
        val ctx = makeContext(backgroundScope, file, lines)
        ctx.setScriptVariableRaw("foo", WslString("script"))
        ctx.setLocalVariable("foo", WslString("local"))
        assertEquals(WslString("local"), ctx.lookupVariable("foo"))
    }

    @Test
    fun deleteScriptVariable() = runTest {
        val (file, lines) = script("delscript", "echo hi")
        val ctx = makeContext(backgroundScope, file, lines)
        ctx.setScriptVariableRaw("foo", WslString("bar"))
        ctx.deleteScriptVariable("foo")
        assertNull(ctx.lookupVariable("foo"))
        assertFalse(ctx.hasScriptVariable("foo"))
    }

    @Test
    fun deleteLocalVariable() = runTest {
        val (file, lines) = script("dellocal", "echo hi")
        val ctx = makeContext(backgroundScope, file, lines)
        ctx.setLocalVariable("foo", WslString("bar"))
        ctx.deleteLocalVariable("foo")
        assertNull(ctx.lookupVariable("foo"))
    }

    @Test
    fun builtInScriptVariablesExist() = runTest {
        val (file, lines) = script("builtins", "echo hi")
        val ctx = makeContext(backgroundScope, file, lines)
        assertTrue(ctx.hasScriptVariable("right"))
        assertTrue(ctx.hasScriptVariable("left"))
        assertTrue(ctx.hasScriptVariable("character"))
        assertFalse(ctx.hasScriptVariable("definitelyNotABuiltin"))
    }

    @Test
    fun storedVariableLookupFallsThroughToGlobal() = runTest(UnconfinedTestDispatcher()) {
        val dao = FakeVariableDao()
        dao.save(VariableEntity("testchar", "foo", "bar"))
        val client = FakeWarlockClient(characterId = "testchar")
        val (file, lines) = script("global", "echo hi")
        // Unconfined dispatcher so the globalVariables stateIn collector runs eagerly.
        val ctx = makeContext(backgroundScope, file, lines, client = client, variableDao = dao)
        assertEquals(WslString("bar"), ctx.lookupVariable("foo"))
    }

    @Test
    fun getNextLineAdvancesThenReturnsNull() = runTest {
        val (file, lines) = script("advance", "echo one\necho two")
        val ctx = makeContext(backgroundScope, file, lines)
        assertSameLine(lines[0], ctx.getNextLine())
        assertSameLine(lines[1], ctx.getNextLine())
        assertNull(ctx.getNextLine())
    }

    @Test
    fun gotoJumpsToLabel() = runTest {
        val (file, lines) = script("goto", "echo a\ntarget: echo b\necho c")
        val ctx = makeContext(backgroundScope, file, lines)
        ctx.goto("target")
        assertSameLine(lines[1], ctx.getNextLine())
    }

    @Test
    fun gotoIsCaseInsensitive() = runTest {
        val (file, lines) = script("gotocase", "echo a\nTarget: echo b")
        val ctx = makeContext(backgroundScope, file, lines)
        ctx.goto("TARGET")
        assertSameLine(lines[1], ctx.getNextLine())
    }

    @Test
    fun gotoMissingLabelThrows() = runTest {
        val (file, lines) = script("gotomissing", "echo a\necho b")
        val ctx = makeContext(backgroundScope, file, lines)
        assertFailsWith<WslRuntimeException> {
            ctx.goto("nope")
        }
    }

    @Test
    fun gotoFallsBackToLabelError() = runTest {
        val (file, lines) = script("labelerror", "echo a\nlabelError: echo b")
        val ctx = makeContext(backgroundScope, file, lines)
        ctx.goto("nonexistent")
        assertSameLine(lines[1], ctx.getNextLine())
    }

    @Test
    fun gosubPushesFrameWithArguments() = runTest {
        val (file, lines) = script("gosub", "echo start\nsub: echo in sub")
        val ctx = makeContext(backgroundScope, file, lines)
        ctx.gosub("sub", "alpha beta")
        assertSameLine(lines[1], ctx.getNextLine())
        assertEquals(WslString("alpha"), ctx.lookupVariable("arg1"))
        assertEquals(WslString("beta"), ctx.lookupVariable("arg2"))
        assertEquals(WslString("alpha beta"), ctx.lookupVariable("argstr"))
        assertNotNull(ctx.lookupVariable("args"))
    }

    @Test
    fun gosubReturnPopsFrame() = runTest {
        val (file, lines) = script("gosubreturn", "echo start\nsub: echo in sub")
        val ctx = makeContext(backgroundScope, file, lines)
        ctx.gosub("sub", "alpha")
        assertEquals(WslString("alpha"), ctx.lookupVariable("arg1"))
        ctx.gosubReturn()
        // arg1 was local to the popped frame
        assertNull(ctx.lookupVariable("arg1"))
    }

    @Test
    fun gosubReturnAtTopLevelThrows() = runTest {
        val (file, lines) = script("topreturn", "echo hi")
        val ctx = makeContext(backgroundScope, file, lines)
        assertFailsWith<WslRuntimeException> {
            ctx.gosubReturn()
        }
    }

    @Test
    fun matchWaitWithNoMatchesThrows() = runTest {
        val (file, lines) = script("nomatch", "echo hi")
        val ctx = makeContext(backgroundScope, file, lines)
        assertFailsWith<WslRuntimeException> {
            ctx.matchWait(null)
        }
    }

    @Test
    fun listenerReceivesTextEvents() = runTest(UnconfinedTestDispatcher()) {
        val client = FakeWarlockClient()
        val (file, lines) = script("listener", "echo hi")
        val ctx = makeContext(backgroundScope, file, lines, client = client)
        val received = mutableListOf<String>()
        ctx.addListener("l") { received += it }
        client.emit(ClientTextEvent("hello world"))
        assertEquals(listOf("hello world"), received)
    }

    @Test
    fun removedListenerStopsReceiving() = runTest(UnconfinedTestDispatcher()) {
        val client = FakeWarlockClient()
        val (file, lines) = script("removelistener", "echo hi")
        val ctx = makeContext(backgroundScope, file, lines, client = client)
        val received = mutableListOf<String>()
        ctx.addListener("l") { received += it }
        ctx.removeListener("l")
        client.emit(ClientTextEvent("hello"))
        assertTrue(received.isEmpty())
    }

    @Test
    fun clearListenersStopsAll() = runTest(UnconfinedTestDispatcher()) {
        val client = FakeWarlockClient()
        val (file, lines) = script("clearlisteners", "echo hi")
        val ctx = makeContext(backgroundScope, file, lines, client = client)
        val received = mutableListOf<String>()
        ctx.addListener("a") { received += it }
        ctx.addListener("b") { received += it }
        ctx.clearListeners()
        client.emit(ClientTextEvent("hello"))
        assertTrue(received.isEmpty())
    }

    private fun assertSameLine(expected: WslLine, actual: WslLine?) {
        assertNotNull(actual)
        assertEquals(expected.lineNumber, actual.lineNumber)
        assertEquals(expected.labels, actual.labels)
    }
}
