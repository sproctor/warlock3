package warlockfe.warlock3.scripting.lua

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlinx.io.writeString
import kotlinx.serialization.json.Json
import warlockfe.warlock3.core.client.ClientEvent
import warlockfe.warlock3.core.client.ClientGmcpEvent
import warlockfe.warlock3.core.client.ClientTextEvent
import warlockfe.warlock3.core.prefs.repositories.VariableRepository
import warlockfe.warlock3.core.script.ScriptStatus
import warlockfe.warlock3.scripting.wsl.FakeScriptManager
import warlockfe.warlock3.scripting.wsl.FakeWarlockClient
import warlockfe.warlock3.scripting.wsl.newTestConfigStore
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private var luaTestSeq = 0

/**
 * End-to-end coverage of the Lua engine: each test writes a small script to a temp file, runs it
 * through a real [LuaScriptInstance] against in-memory fakes, and asserts on what reached the
 * client. These use real time (the interpreter blocks a real thread), so waits are generous.
 */
class LuaScriptInstanceTest {
    private fun writeScript(content: String): Path {
        val path = Path(SystemTemporaryDirectory, "lua-test-${luaTestSeq++}.lua")
        SystemFileSystem.sink(path).buffered().use { it.writeString(content) }
        return path
    }

    private fun createInstance(
        script: String,
        variableRepository: VariableRepository = VariableRepository(newTestConfigStore()),
    ): LuaScriptInstance =
        LuaScriptInstance(
            id = 1L,
            name = "test",
            file = writeScript(script),
            content = null,
            variableRepository = variableRepository,
            scriptManager = FakeScriptManager(),
            fileSystem = SystemFileSystem,
        )

    /** An instance run from the string itself, as an action button's script or the MUD's is. */
    private fun createInlineInstance(script: String): LuaScriptInstance =
        LuaScriptInstance(
            id = 1L,
            name = "inline",
            file = null,
            content = script,
            variableRepository = VariableRepository(newTestConfigStore()),
            scriptManager = FakeScriptManager(),
            fileSystem = SystemFileSystem,
        )

    /** Emits [event] every 50ms until [done] holds, since the script subscribes asynchronously. */
    private suspend fun FakeWarlockClient.emitUntil(
        event: ClientEvent,
        done: () -> Boolean,
    ) {
        withTimeout(10.seconds) {
            while (!done()) {
                emit(event)
                delay(50.milliseconds)
            }
        }
    }

    private suspend fun LuaScriptInstance.awaitStopped(timeout: Duration = 30.seconds) {
        withTimeout(timeout) { statusFlow.first { it == ScriptStatus.Stopped } }
    }

    /** Starts [script] and waits for it to run to completion. */
    private fun runScript(
        script: String,
        argumentString: String = "",
        client: FakeWarlockClient = FakeWarlockClient(),
        variableRepository: VariableRepository = VariableRepository(newTestConfigStore()),
    ): FakeWarlockClient {
        val instance = createInstance(script, variableRepository)
        runBlocking {
            instance.start(client, argumentString, onStop = {}, commandHandler = { client.sendCommand(it) })
            instance.awaitStopped()
        }
        return client
    }

    private fun FakeWarlockClient.printedText(): List<String> = printed.map { it.toText() }

    @Test
    fun echoAndPrintGoToTheClient() {
        val client =
            runScript(
                """
                echo("hello")
                print("world", 42)
                """.trimIndent(),
            )
        assertContains(client.printedText(), "hello")
        assertContains(client.printedText(), "world\t42")
    }

    @Test
    fun argumentsArePassedAsVarargs() {
        val client =
            runScript(
                """
                local a, b = ...
                echo(a .. "-" .. b)
                """.trimIndent(),
                argumentString = "foo bar",
            )
        assertContains(client.printedText(), "foo-bar")
    }

    @Test
    fun putSendsTheCommand() {
        val client = runScript("""put("look")""")
        assertEquals(listOf("look"), client.sentCommands)
    }

    @Test
    fun variablesReadAndWriteTheRepository() {
        val variableRepository = VariableRepository(newTestConfigStore())
        val client =
            runScript(
                """
                variables.target = "goblin"
                echo(variables.target)
                """.trimIndent(),
                variableRepository = variableRepository,
            )
        assertContains(client.printedText(), "goblin")
        assertEquals("goblin", variableRepository.getVariable("testchar", "target"))
    }

    @Test
    fun exitStopsTheScriptSilently() {
        val client =
            runScript(
                """
                echo("before")
                exit()
                echo("after")
                """.trimIndent(),
            )
        assertContains(client.printedText(), "before")
        assertFalse(client.printedText().contains("after"))
        assertFalse(client.printedText().any { it.contains("Script error") })
    }

    @Test
    fun runtimeErrorsAreReported() {
        val client = runScript("""error("boom")""")
        assertTrue(client.printedText().any { it.contains("Script error") && it.contains("boom") })
    }

    @Test
    fun stopInterruptsAPause() {
        val client = FakeWarlockClient()
        val instance =
            createInstance(
                """
                echo("before")
                pause(30)
                echo("after")
                """.trimIndent(),
            )
        runBlocking {
            instance.start(client, "", onStop = {}, commandHandler = { client.sendCommand(it) })
            withTimeout(10.seconds) {
                while (!client.printedText().contains("before")) {
                    delay(20.milliseconds)
                }
            }
            instance.stop()
            instance.awaitStopped(10.seconds)
        }
        assertFalse(client.printedText().contains("after"))
    }

    @Test
    fun busyLoopCanBeStopped() {
        val client = FakeWarlockClient()
        val instance = createInstance("while true do end")
        runBlocking {
            instance.start(client, "", onStop = {}, commandHandler = { client.sendCommand(it) })
            delay(300.milliseconds)
            instance.stop()
            instance.awaitStopped(10.seconds)
        }
    }

    @Test
    fun suspendPausesExecutionUntilResume() {
        val client = FakeWarlockClient()
        val instance =
            createInstance(
                """
                pause(0.3)
                echo("after")
                """.trimIndent(),
            )
        runBlocking {
            instance.start(client, "", onStop = {}, commandHandler = { client.sendCommand(it) })
            instance.suspend()
            delay(700.milliseconds)
            assertFalse(client.printedText().contains("after"))
            instance.resume()
            instance.awaitStopped()
        }
        assertContains(client.printedText(), "after")
    }

    @Test
    fun matchListReturnsTheMatchedObject() {
        val client = FakeWarlockClient()
        val instance =
            createInstance(
                """
                local m = MatchList()
                m:addMatch("apple", "got fruit")
                m:addMatchRe("gob.in", "got monster")
                echo(m:wait())
                """.trimIndent(),
            )
        runBlocking {
            instance.start(client, "", onStop = {}, commandHandler = { client.sendCommand(it) })
            // Keep emitting until the script (which subscribes asynchronously) sees a line.
            val emitter =
                launch {
                    while (true) {
                        client.emit(ClientTextEvent("a goblin arrives"))
                        delay(50.milliseconds)
                    }
                }
            instance.awaitStopped()
            emitter.cancel()
        }
        assertContains(client.printedText(), "got monster")
    }

    @Test
    fun inlineScriptsRunFromTheString() {
        val client = FakeWarlockClient()
        val instance = createInlineInstance("""echo("from a string")""")
        runBlocking {
            instance.start(client, "", onStop = {}, commandHandler = { client.sendCommand(it) })
            instance.awaitStopped()
        }
        assertContains(client.printedText(), "from a string")
    }

    @Test
    fun gmcpHandlersKeepTheScriptAliveAndGetTheMessageAsATable() {
        val client = FakeScriptableClient()
        val instance =
            createInlineInstance(
                """
                onGmcp("Char.Vitals", function(data, name)
                    echo(name .. " hp=" .. data.hp .. " tags=" .. #data.tags .. " none=" .. tostring(data.none))
                end)
                -- A package name hears every message under it.
                onGmcp("char", function(data) echo("under char") end)
                onGmcp("Core.Ping", function(data) echo("ping " .. tostring(data)) end)
                echo("registered")
                """.trimIndent(),
            )
        runBlocking {
            instance.start(client, "", onStop = {}, commandHandler = { client.sendCommand(it) })
            client.emitUntil(ClientGmcpEvent("Char.Vitals", """{"hp":10,"tags":["a","b"],"none":null}""")) {
                client.printedText().contains("Char.Vitals hp=10 tags=2 none=nil")
            }
            assertContains(client.printedText(), "under char")
            // Still running, waiting for more.
            assertEquals(ScriptStatus.Running, instance.status)
            client.emitUntil(ClientGmcpEvent("Core.Ping", "")) { client.printedText().contains("ping nil") }
            // The script is about the connection: it ends when the connection does.
            client.disconnected.value = true
            instance.awaitStopped(10.seconds)
        }
        assertFalse(client.printedText().any { it.contains("Script error") })
    }

    @Test
    fun lineHandlersMatchPatternsAndSetTheRoundtime() {
        val client = FakeScriptableClient()
        client.setCurrentTime(Instant.fromEpochSeconds(1000))
        val instance =
            createInlineInstance(
                """
                onLine("^Roundtime: (%d+)", function(seconds) setRoundTime(tonumber(seconds)) end)
                onLine("^Casting", function() setCastTime(2.5) end)
                onLine("^Clear", function() setRoundTime(0) setCastTime(0) end)
                onLine(function(line) echo("saw " .. line) end)
                """.trimIndent(),
            )
        runBlocking {
            instance.start(client, "", onStop = {}, commandHandler = { client.sendCommand(it) })
            client.emitUntil(ClientTextEvent("Roundtime: 3 sec.")) { client.roundTimeEnd.value == 1003L }
            assertContains(client.printedText(), "saw Roundtime: 3 sec.")
            client.emitUntil(ClientTextEvent("Casting a spell")) { client.castTimeEnd.value == 1002L }
            client.emitUntil(ClientTextEvent("Clear")) { client.roundTimeEnd.value == null }
            assertNull(client.castTimeEnd.value)
            instance.stop()
            instance.awaitStopped(10.seconds)
        }
        assertFalse(client.printedText().any { it.contains("Script error") })
    }

    @Test
    fun aFailingHandlerIsReportedAndTheOthersStillRun() {
        val client = FakeWarlockClient()
        val instance =
            createInlineInstance(
                """
                onLine(function(line) error("bad " .. line) end)
                onLine(function(line) echo("also " .. line) end)
                """.trimIndent(),
            )
        runBlocking {
            instance.start(client, "", onStop = {}, commandHandler = { client.sendCommand(it) })
            client.emitUntil(ClientTextEvent("one")) { client.printedText().any { it.contains("bad one") } }
            assertTrue(client.printedText().any { it.contains("Script error") && it.contains("bad one") })
            assertContains(client.printedText(), "also one")
            assertEquals(ScriptStatus.Running, instance.status)
            instance.stop()
            instance.awaitStopped(10.seconds)
        }
    }

    @Test
    fun exitInsideAHandlerEndsTheScriptQuietly() {
        val client = FakeWarlockClient()
        val instance =
            createInlineInstance(
                """
                onLine(function(line)
                    echo("bye")
                    exit()
                end)
                """.trimIndent(),
            )
        runBlocking {
            instance.start(client, "", onStop = {}, commandHandler = { client.sendCommand(it) })
            client.emitUntil(ClientTextEvent("x")) { instance.status == ScriptStatus.Stopped }
        }
        assertContains(client.printedText(), "bye")
        assertFalse(client.printedText().any { it.contains("Script error") })
    }

    @Test
    fun sendGmcpSendsStringsAsTheyAreAndTablesAsJson() {
        val client = FakeScriptableClient()
        val instance =
            createInlineInstance(
                """
                sendGmcp("Core.Supports.Add", {"Char.Status 1"})
                sendGmcp("Core.Ping")
                sendGmcp("Raw", '{"a":1}')
                sendGmcp("Obj", { n = 1, ok = true })
                """.trimIndent(),
            )
        runBlocking {
            instance.start(client, "", onStop = {}, commandHandler = { client.sendCommand(it) })
            instance.awaitStopped()
        }
        assertEquals(
            listOf(
                "Core.Supports.Add" to """["Char.Status 1"]""",
                "Core.Ping" to "",
                "Raw" to """{"a":1}""",
            ),
            client.sentGmcp.take(3),
        )
        // A Lua table's keys come in no particular order.
        assertEquals("Obj", client.sentGmcp[3].first)
        assertEquals(Json.parseToJsonElement("""{"n":1,"ok":true}"""), Json.parseToJsonElement(client.sentGmcp[3].second))
    }

    @Test
    fun theStatusFunctionsNeedATelnetConnection() {
        val client = runScript("""setRoundTime(3)""")
        assertTrue(
            client.printedText().any { it.contains("Script error") && it.contains("only available on a telnet connection") },
            client.printedText().toString(),
        )
    }

    @Test
    fun anEventDuringTheRestOfTheScriptIsNotLost() {
        val client = FakeWarlockClient()
        val instance =
            createInlineInstance(
                """
                onLine(function(line) echo("got " .. line) end)
                echo("registered")
                pause(2)
                echo("done")
                """.trimIndent(),
            )
        runBlocking {
            instance.start(client, "", onStop = {}, commandHandler = { client.sendCommand(it) })
            withTimeout(10.seconds) {
                while (!client.printedText().contains("registered")) delay(20.milliseconds)
            }
            // Sent while the script is still in its pause, and no longer once it says the pause
            // is over: gathered from the moment the handler was registered, the event is served
            // once the script reaches its last line. (No wall-clock assumption: a slow machine
            // only lengthens the pause.)
            var sent = 0
            withTimeout(10.seconds) {
                while (!client.printedText().contains("done")) {
                    client.emit(ClientTextEvent("early"))
                    sent++
                    delay(20.milliseconds)
                }
            }
            assertTrue(sent > 0)
            withTimeout(10.seconds) {
                while (!client.printedText().contains("got early")) delay(20.milliseconds)
            }
            instance.stop()
            instance.awaitStopped(10.seconds)
        }
    }
}
