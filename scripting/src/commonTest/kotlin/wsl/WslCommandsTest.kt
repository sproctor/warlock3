package warlockfe.warlock3.scripting.wsl

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlinx.io.writeString
import warlockfe.warlock3.core.client.ClientTextEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Exercises the WSL command table by invoking entries directly against a test [WslContext]. */
class WslCommandsTest {
    private suspend fun WslContext.command(
        name: String,
        args: String = "",
    ) {
        val command = wslCommands[name] ?: throw AssertionError("No such command: $name")
        command(this, args)
    }

    private fun parseLines(content: String): List<WslLine> {
        val path = Path(SystemTemporaryDirectory, "wslcmd.wsl")
        SystemFileSystem.sink(path).buffered().use { it.writeString(content) }
        try {
            return WslScript("cmd", path, SystemFileSystem).parse()
        } finally {
            SystemFileSystem.delete(path)
        }
    }

    // --- variable assignment ---

    @Test
    fun varSetsScriptVariable() =
        runTest {
            val ctx = buildTestContext(backgroundScope)
            ctx.command("var", "name value")
            assertEquals(WslString("value"), ctx.lookupVariable("name"))
        }

    @Test
    fun varWithBlankNameThrows() =
        runTest {
            val ctx = buildTestContext(backgroundScope)
            assertFailsWith<WslRuntimeException> { ctx.command("var", "") }
        }

    @Test
    fun unsetvarRemovesScriptVariable() =
        runTest {
            val ctx = buildTestContext(backgroundScope)
            ctx.command("var", "name value")
            ctx.command("unsetvar", "name")
            assertNull(ctx.lookupVariable("name"))
        }

    @Test
    fun localSetsLocalVariable() =
        runTest {
            val ctx = buildTestContext(backgroundScope)
            ctx.command("local", "name value")
            assertEquals(WslString("value"), ctx.lookupVariable("name"))
        }

    @Test
    fun unsetlocalRemovesLocalVariable() =
        runTest {
            val ctx = buildTestContext(backgroundScope)
            ctx.command("local", "name value")
            ctx.command("unsetlocal", "name")
            assertNull(ctx.lookupVariable("name"))
        }

    @Test
    fun saveSetsSVariable() =
        runTest {
            val ctx = buildTestContext(backgroundScope)
            ctx.command("save", "some text")
            assertEquals(WslString("some text"), ctx.lookupVariable("s"))
        }

    // --- counter ---

    @Test
    fun counterSet() =
        runTest {
            val ctx = buildTestContext(backgroundScope)
            ctx.command("counter", "set 10")
            assertEquals("10", ctx.lookupVariable("c").toString())
        }

    @Test
    fun counterAddWithoutOperandIncrementsByOne() =
        runTest {
            val ctx = buildTestContext(backgroundScope)
            ctx.command("counter", "add")
            assertEquals("1", ctx.lookupVariable("c").toString())
        }

    @Test
    fun counterAddSubtractMultiplyDivide() =
        runTest {
            val ctx = buildTestContext(backgroundScope)
            ctx.command("counter", "set 10")
            ctx.command("counter", "add 5")
            assertEquals("15", ctx.lookupVariable("c").toString())
            ctx.command("counter", "subtract 3")
            assertEquals("12", ctx.lookupVariable("c").toString())
            ctx.command("counter", "multiply 2")
            assertEquals("24", ctx.lookupVariable("c").toString())
            ctx.command("counter", "divide 4")
            assertEquals("6", ctx.lookupVariable("c").toString())
        }

    @Test
    fun counterDivideByZeroThrows() =
        runTest {
            val ctx = buildTestContext(backgroundScope)
            ctx.command("counter", "set 10")
            assertFailsWith<WslRuntimeException> { ctx.command("counter", "divide 0") }
        }

    @Test
    fun counterUnsupportedOperatorThrows() =
        runTest {
            val ctx = buildTestContext(backgroundScope)
            assertFailsWith<WslRuntimeException> { ctx.command("counter", "bogus 5") }
        }

    @Test
    fun counterNonNumericOperandThrows() =
        runTest {
            val ctx = buildTestContext(backgroundScope)
            assertFailsWith<WslRuntimeException> { ctx.command("counter", "add abc") }
        }

    // --- random ---

    @Test
    fun randomSetsRWithinRange() =
        runTest {
            val ctx = buildTestContext(backgroundScope)
            repeat(20) {
                ctx.command("random", "1 10")
                val value = ctx.lookupVariable("r").toString().toInt()
                assertTrue(value in 1..9, "random produced out-of-range value: $value")
            }
        }

    @Test
    fun randomWithMinNotLessThanMaxThrows() =
        runTest {
            val ctx = buildTestContext(backgroundScope)
            assertFailsWith<WslRuntimeException> { ctx.command("random", "10 5") }
        }

    @Test
    fun randomWithInvalidArgsThrows() =
        runTest {
            val ctx = buildTestContext(backgroundScope)
            assertFailsWith<WslRuntimeException> { ctx.command("random", "abc") }
        }

    // --- shift ---

    @Test
    fun shiftMovesNumberedVariablesDown() =
        runTest {
            val ctx = buildTestContext(backgroundScope)
            ctx.setScriptVariableRaw("0", WslString("a b c"))
            ctx.setScriptVariableRaw("1", WslString("a"))
            ctx.setScriptVariableRaw("2", WslString("b"))
            ctx.setScriptVariableRaw("3", WslString("c"))
            ctx.command("shift")
            assertEquals(WslString("b"), ctx.lookupVariable("1"))
            assertEquals(WslString("c"), ctx.lookupVariable("2"))
            assertNull(ctx.lookupVariable("3"))
            assertEquals(WslString("b c"), ctx.lookupVariable("0"))
        }

    // --- maps ---

    @Test
    fun mapAddCreatesMapAndSetsProperty() =
        runTest {
            val ctx = buildTestContext(backgroundScope)
            ctx.command("mapadd", "mymap key value")
            assertEquals("value", ctx.lookupVariable("mymap")?.getProperty("key")?.toString())
        }

    @Test
    fun mapAddOnNonMapThrows() =
        runTest {
            val ctx = buildTestContext(backgroundScope)
            ctx.setScriptVariableRaw("x", WslString("notamap"))
            assertFailsWith<WslRuntimeException> { ctx.command("mapadd", "x key value") }
        }

    @Test
    fun mapAddWithMissingArgumentsThrows() =
        runTest {
            val ctx = buildTestContext(backgroundScope)
            assertFailsWith<WslRuntimeException> { ctx.command("mapadd", "onlyname") }
        }

    @Test
    fun setArrayPopulatesIndexedProperties() =
        runTest {
            val ctx = buildTestContext(backgroundScope)
            ctx.command("setarray", "arr a b c")
            val arr = ctx.lookupVariable("arr")
            assertNotNull(arr)
            assertEquals("a b c", arr.getProperty("0").toString())
            assertEquals("a", arr.getProperty("1").toString())
            assertEquals("b", arr.getProperty("2").toString())
            assertEquals("c", arr.getProperty("3").toString())
        }

    // --- control flow ---

    @Test
    fun gotoJumpsToLabel() =
        runTest {
            val lines = parseLines("echo a\ntarget: echo b")
            val ctx = buildTestContext(backgroundScope, lines = lines)
            ctx.command("goto", "target")
            assertEquals(listOf("target"), ctx.getNextLine()?.labels)
        }

    @Test
    fun gotoWithBlankLabelThrows() =
        runTest {
            val ctx = buildTestContext(backgroundScope)
            assertFailsWith<WslRuntimeException> { ctx.command("goto", "") }
        }

    @Test
    fun gosubSetsArguments() =
        runTest {
            val lines = parseLines("echo a\nsub: echo b")
            val ctx = buildTestContext(backgroundScope, lines = lines)
            ctx.command("gosub", "sub alpha beta")
            assertEquals(WslString("alpha"), ctx.lookupVariable("arg1"))
            assertEquals(WslString("beta"), ctx.lookupVariable("arg2"))
        }

    @Test
    fun gosubWithNoLabelThrows() =
        runTest {
            val ctx = buildTestContext(backgroundScope)
            assertFailsWith<WslRuntimeException> { ctx.command("gosub", "") }
        }

    @Test
    fun returnOutsideSubroutineThrows() =
        runTest {
            val ctx = buildTestContext(backgroundScope)
            assertFailsWith<WslRuntimeException> { ctx.command("return") }
        }

    @Test
    fun exitStopsScript() =
        runTest {
            val scriptManager = FakeScriptManager()
            val ctx = buildTestContext(backgroundScope, scriptManager = scriptManager)
            ctx.command("exit")
            assertTrue(scriptManager.stateChanges.isNotEmpty())
        }

    @Test
    fun ifNExecutesCommandWhenVariableExists() =
        runTest {
            val ctx = buildTestContext(backgroundScope)
            ctx.setScriptVariableRaw("1", WslString("present"))
            ctx.command("if_1", "var result yes")
            assertEquals(WslString("yes"), ctx.lookupVariable("result"))
        }

    @Test
    fun ifNSkipsCommandWhenVariableMissing() =
        runTest {
            val ctx = buildTestContext(backgroundScope)
            ctx.command("if_2", "var result yes")
            assertNull(ctx.lookupVariable("result"))
        }

    // --- timer ---

    @Test
    fun timerStartCreatesTimerAndClearRemovesIt() =
        runTest {
            val ctx = buildTestContext(backgroundScope)
            ctx.command("timer", "start")
            assertTrue(ctx.lookupVariable("t") is WslTimer)
            ctx.command("timer", "clear")
            assertNull(ctx.lookupVariable("t"))
        }

    // --- output and commands ---

    @Test
    fun echoPrintsToClient() =
        runTest {
            val client = FakeWarlockClient()
            val ctx = buildTestContext(backgroundScope, client = client)
            ctx.command("echo", "hello world")
            assertEquals("hello world", client.printed.single().toText())
        }

    @Test
    fun sendForwardsCommand() =
        runTest {
            val client = FakeWarlockClient()
            val ctx = buildTestContext(backgroundScope, client = client)
            ctx.command("send", "look")
            assertEquals(listOf("look"), client.sentCommands)
        }

    @Test
    fun putForwardsCommand() =
        runTest {
            val client = FakeWarlockClient()
            val ctx = buildTestContext(backgroundScope, client = client)
            ctx.command("put", "north")
            assertEquals(listOf("north"), client.sentCommands)
        }

    @Test
    fun playPlaysSoundAndEchoes() =
        runTest {
            val client = FakeWarlockClient()
            val soundPlayer = FakeSoundPlayer()
            val ctx = buildTestContext(backgroundScope, client = client, soundPlayer = soundPlayer)
            ctx.command("play", "alert.wav")
            assertEquals(listOf("alert.wav"), soundPlayer.playedSounds)
            assertTrue(
                client.printed
                    .single()
                    .toText()
                    .contains("alert.wav"),
            )
        }

    // --- logging ---

    @Test
    fun debugIsSuppressedAtDefaultLevelButInfoIsShown() =
        runTest {
            val client = FakeWarlockClient()
            val ctx = buildTestContext(backgroundScope, client = client)
            ctx.command("debug", "hidden")
            assertTrue(client.scriptDebugMessages.isEmpty())
            ctx.command("info", "shown")
            assertEquals(listOf("shown"), client.scriptDebugMessages)
        }

    @Test
    fun debugLevelLowersThreshold() =
        runTest {
            val client = FakeWarlockClient()
            val ctx = buildTestContext(backgroundScope, client = client)
            ctx.command("debuglevel", "0")
            ctx.command("debug", "now visible")
            assertEquals(listOf("now visible"), client.scriptDebugMessages)
        }

    @Test
    fun debugLevelOutOfRangeThrows() =
        runTest {
            val ctx = buildTestContext(backgroundScope)
            assertFailsWith<WslRuntimeException> { ctx.command("debuglevel", "99") }
        }

    @Test
    fun debugLevelInvalidThrows() =
        runTest {
            val ctx = buildTestContext(backgroundScope)
            assertFailsWith<WslRuntimeException> { ctx.command("debuglevel", "notalevel") }
        }

    @Test
    fun logWithInvalidLevelThrows() =
        runTest {
            val ctx = buildTestContext(backgroundScope)
            assertFailsWith<WslRuntimeException> { ctx.command("log", "notalevel message") }
        }

    // --- matches ---

    @Test
    fun matchWithBlankTextThrows() =
        runTest {
            val ctx = buildTestContext(backgroundScope)
            assertFailsWith<WslRuntimeException> { ctx.command("match", "label") }
        }

    @Test
    fun matchReWithInvalidRegexThrows() =
        runTest {
            val ctx = buildTestContext(backgroundScope)
            assertFailsWith<WslRuntimeException> { ctx.command("matchre", "label notaregex") }
        }

    @Test
    fun matchWaitWithExtraArgumentThrows() =
        runTest {
            val ctx = buildTestContext(backgroundScope)
            ctx.command("match", "label some text")
            assertFailsWith<WslRuntimeException> { ctx.command("matchwait", "5 extra") }
        }

    @Test
    fun matchWaitWithNoMatchesThrows() =
        runTest {
            val ctx = buildTestContext(backgroundScope)
            assertFailsWith<WslRuntimeException> { ctx.command("matchwait", "") }
        }

    // --- highlights and names ---

    @Test
    fun addToHighlightStringsSavesHighlight() =
        runTest {
            val highlightRepository = FakeHighlightRepository()
            val client = FakeWarlockClient(characterId = "testchar")
            val ctx = buildTestContext(backgroundScope, client = client, highlightRepository = highlightRepository)
            ctx.command("addtohighlightstrings", "string=goblin")
            assertEquals(1, highlightRepository.saved.size)
            assertEquals(
                "goblin",
                highlightRepository.saved
                    .single()
                    .second.pattern,
            )
            assertEquals("testchar", highlightRepository.saved.single().first)
        }

    @Test
    fun addToHighlightStringsWithoutStringThrows() =
        runTest {
            val ctx = buildTestContext(backgroundScope)
            assertFailsWith<WslRuntimeException> { ctx.command("addtohighlightstrings", "global=true") }
        }

    @Test
    fun addToHighlightStringsWithMalformedArgumentThrows() =
        runTest {
            val ctx = buildTestContext(backgroundScope)
            assertFailsWith<WslRuntimeException> { ctx.command("addtohighlightstrings", "notakeyvalue") }
        }

    @Test
    fun addToHighlightStringsWithUnknownArgumentThrows() =
        runTest {
            val ctx = buildTestContext(backgroundScope)
            assertFailsWith<WslRuntimeException> { ctx.command("addtohighlightstrings", "bogus=x") }
        }

    @Test
    fun deleteFromHighlightStringsDeletesByPattern() =
        runTest {
            val highlightRepository = FakeHighlightRepository()
            val client = FakeWarlockClient(characterId = "testchar")
            val ctx = buildTestContext(backgroundScope, client = client, highlightRepository = highlightRepository)
            ctx.command("deletefromhighlightstrings", "string=goblin")
            assertEquals(listOf("testchar" to "goblin"), highlightRepository.deletedPatterns)
        }

    @Test
    fun addToHighlightNamesSavesName() =
        runTest {
            val nameRepository = FakeNameRepository()
            val client = FakeWarlockClient(characterId = "testchar")
            val ctx = buildTestContext(backgroundScope, client = client, nameRepository = nameRepository)
            ctx.command("addtohighlightnames", "string=Bob")
            assertEquals("Bob", nameRepository.saved.single().text)
        }

    @Test
    fun deleteFromHighlightNamesDeletesByText() =
        runTest {
            val nameRepository = FakeNameRepository()
            val client = FakeWarlockClient(characterId = "testchar")
            val ctx = buildTestContext(backgroundScope, client = client, nameRepository = nameRepository)
            ctx.command("deletefromhighlightnames", "string=Bob")
            assertEquals(listOf("testchar" to "Bob"), nameRepository.deletedText)
        }

    @Test
    fun waitForReWithInvalidRegexThrows() =
        runTest {
            val ctx = buildTestContext(backgroundScope)
            assertFailsWith<WslRuntimeException> { ctx.command("waitforre", "notaregex") }
        }

    // --- text listeners ---

    @Test
    fun addTextListenerWithoutNameThrows() =
        runTest {
            val ctx = buildTestContext(backgroundScope)
            assertFailsWith<WslRuntimeException> { ctx.command("addtextlistener", "") }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun addTextListenerSetsVariableOnMatchingText() =
        runTest(UnconfinedTestDispatcher()) {
            val client = FakeWarlockClient()
            val ctx = buildTestContext(backgroundScope, client = client)
            ctx.command("addtextlistener", "found hello")
            client.emit(ClientTextEvent("well hello there"))
            assertEquals("well hello there", ctx.lookupVariable("found")?.toString())
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun addTextListenerIgnoresNonMatchingText() =
        runTest(UnconfinedTestDispatcher()) {
            val client = FakeWarlockClient()
            val ctx = buildTestContext(backgroundScope, client = client)
            ctx.command("addtextlistener", "found hello")
            client.emit(ClientTextEvent("goodbye world"))
            assertNull(ctx.lookupVariable("found"))
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun addTextListenerReCapturesRegexMatch() =
        runTest(UnconfinedTestDispatcher()) {
            val client = FakeWarlockClient()
            val ctx = buildTestContext(backgroundScope, client = client)
            ctx.command("addtextlistenerre", "amount /([0-9]+)/")
            client.emit(ClientTextEvent("you have 42 coins"))
            assertEquals("42", ctx.lookupVariable("amount")?.toString())
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun removeTextListenerStopsUpdates() =
        runTest(UnconfinedTestDispatcher()) {
            val client = FakeWarlockClient()
            val ctx = buildTestContext(backgroundScope, client = client)
            ctx.command("addtextlistener", "found")
            ctx.command("removetextlistener", "found")
            client.emit(ClientTextEvent("anything"))
            assertNull(ctx.lookupVariable("found"))
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun clearTextListenersStopsUpdates() =
        runTest(UnconfinedTestDispatcher()) {
            val client = FakeWarlockClient()
            val ctx = buildTestContext(backgroundScope, client = client)
            ctx.command("addtextlistener", "found")
            ctx.command("cleartextlisteners")
            client.emit(ClientTextEvent("anything"))
            assertNull(ctx.lookupVariable("found"))
        }
}
