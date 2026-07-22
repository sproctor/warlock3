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
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

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
            variableRepository = variableRepository,
            scriptManager = FakeScriptManager(),
            fileSystem = SystemFileSystem,
        )

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
                    delay(20)
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
            delay(300)
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
            delay(700)
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
                        delay(50)
                    }
                }
            instance.awaitStopped()
            emitter.cancel()
        }
        assertContains(client.printedText(), "got monster")
    }
}
