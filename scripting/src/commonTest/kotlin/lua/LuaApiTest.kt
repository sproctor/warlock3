package warlockfe.warlock3.scripting.lua

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlinx.io.writeString
import warlockfe.warlock3.core.client.ClientPromptEvent
import warlockfe.warlock3.core.client.ClientTextEvent
import warlockfe.warlock3.core.client.LineInterceptor
import warlockfe.warlock3.core.client.ProtocolTap
import warlockfe.warlock3.core.client.SendCommandType
import warlockfe.warlock3.core.client.WarlockClient
import warlockfe.warlock3.core.prefs.repositories.VariableRepository
import warlockfe.warlock3.core.script.ScriptData
import warlockfe.warlock3.core.script.ScriptInstance
import warlockfe.warlock3.core.script.ScriptManager
import warlockfe.warlock3.core.script.ScriptStatus
import warlockfe.warlock3.scripting.wsl.FakeScriptManager
import warlockfe.warlock3.scripting.wsl.FakeWarlockClient
import warlockfe.warlock3.scripting.wsl.newTestConfigStore
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private var apiTestSeq = 0

/** A fake connection with game state, line interception, and replies to commands. */
private class GameClient : FakeWarlockClient() {
    private val interceptors = MutableStateFlow<List<LineInterceptor>>(emptyList())

    private val history = mutableListOf<String>()
    val taps = mutableListOf<(String) -> Unit>()

    /** A line of protocol arriving: kept for later taps and given to the current ones. */
    fun protocol(line: String) {
        history += line
        taps.toList().forEach { it(line) }
    }

    override fun tapProtocol(listener: (String) -> Unit): ProtocolTap {
        taps += listener
        return ProtocolTap(history.toList()) { taps -= listener }
    }

    /** Called with each command sent, quietly or not, before the send returns. */
    var onCommand: suspend GameClient.(String) -> Unit = {}

    val quietCommands = mutableListOf<String>()

    override fun addLineInterceptor(interceptor: LineInterceptor): () -> Unit {
        interceptors.update { it + interceptor }
        return { interceptors.update { it - interceptor } }
    }

    suspend fun line(text: String) {
        if (interceptors.value.none { it.intercept(text) }) emit(ClientTextEvent(text))
    }

    suspend fun prompt() {
        interceptors.value.forEach { it.prompt() }
        emit(ClientPromptEvent)
    }

    override suspend fun sendCommand(line: String): SendCommandType {
        super.sendCommand(line)
        onCommand(line)
        return SendCommandType.COMMAND
    }

    override suspend fun sendQuietCommand(line: String): SendCommandType {
        quietCommands += line
        onCommand(line)
        return SendCommandType.COMMAND
    }
}

/** A script manager whose scripts run for [runFor] and then finish. */
private class TimedScriptManager(
    private val runFor: kotlin.time.Duration = 300.milliseconds,
) : ScriptManager {
    override val runningScripts = MutableStateFlow<Map<Long, ScriptData>>(emptyMap())
    private val scope = CoroutineScope(Dispatchers.Default)
    private var nextId = 100L
    val stopped = mutableListOf<String>()

    override suspend fun startScript(
        client: WarlockClient,
        command: String,
        commandHandler: suspend (String) -> SendCommandType,
    ): ScriptInstance? {
        val name = command.substringBefore(' ')
        if (name == "missing") return null
        val script = StubScript(nextId++, name, this)
        runningScripts.update { it + (script.id to ScriptData(ScriptStatus.Running, script)) }
        scope.launch {
            delay(runFor)
            runningScripts.update { it - script.id }
        }
        return script
    }

    override suspend fun startScript(
        client: WarlockClient,
        file: Path,
        commandHandler: suspend (String) -> SendCommandType,
    ) = Unit

    override suspend fun startScript(
        client: WarlockClient,
        name: String,
        contents: String,
        commandHandler: suspend (String) -> SendCommandType,
        extension: String,
    ) = Unit

    override fun findScriptInstance(description: String): ScriptInstance? = null

    override fun scriptStateChanged(instance: ScriptInstance) = Unit

    private class StubScript(
        override val id: Long,
        override val name: String,
        private val manager: TimedScriptManager,
    ) : ScriptInstance {
        override val status = ScriptStatus.Running

        override fun start(
            client: WarlockClient,
            argumentString: String,
            onStop: () -> Unit,
            commandHandler: suspend (String) -> SendCommandType,
        ) = Unit

        override fun stop() {
            manager.stopped += name
            manager.runningScripts.update { it - id }
        }

        override fun suspend() = Unit

        override fun resume() = Unit
    }
}

/**
 * The script API beyond the basics: game state, regular expressions, the line buffer and its
 * waits, capture, handlers during waits, require, other scripts, and storage. Each script asserts
 * in Lua and echoes "ok" at the end; a failed assertion shows up as a script error.
 */
class LuaApiTest {
    private fun tempDir(name: String): Path {
        // Random, so a folder left by an earlier run is never reused.
        val dir = Path(SystemTemporaryDirectory, "lua-api-$name-${Random.nextLong().toULong()}")
        SystemFileSystem.createDirectories(dir)
        return dir
    }

    private fun writeFile(
        path: Path,
        content: String,
    ) {
        path.parent?.let { SystemFileSystem.createDirectories(it) }
        SystemFileSystem.sink(path).buffered().use { it.writeString(content) }
    }

    private fun instance(
        script: String,
        scriptManager: ScriptManager = FakeScriptManager(),
        scriptDirs: List<Path> = emptyList(),
        storageDir: Path? = null,
    ): LuaScriptInstance {
        val file = Path(SystemTemporaryDirectory, "lua-api-${apiTestSeq++}.lua")
        writeFile(file, script)
        return LuaScriptInstance(
            id = 1L,
            name = "test",
            file = file,
            content = null,
            variableRepository = VariableRepository(newTestConfigStore()),
            scriptManager = scriptManager,
            fileSystem = SystemFileSystem,
            scriptDirs = { scriptDirs },
            storageDir = storageDir,
        )
    }

    private fun LuaScriptInstance.launch(client: GameClient) {
        start(client, "", onStop = {}, commandHandler = { client.sendCommand(it) })
    }

    private suspend fun LuaScriptInstance.awaitStopped() {
        withTimeout(30.seconds) { statusFlow.first { it == ScriptStatus.Stopped } }
    }

    private fun GameClient.text(): List<String> = printed.map { it.toText() }

    private suspend fun GameClient.awaitPrinted(text: String) {
        withTimeout(10.seconds) {
            while (text !in text()) delay(20.milliseconds)
        }
    }

    /** Runs [script] to the end and checks it echoed "ok" with no error. */
    private fun runOk(
        script: String,
        client: GameClient = GameClient(),
        scriptManager: ScriptManager = FakeScriptManager(),
        scriptDirs: List<Path> = emptyList(),
        storageDir: Path? = null,
        whileRunning: suspend GameClient.() -> Unit = {},
    ): GameClient {
        val instance = instance(script, scriptManager, scriptDirs, storageDir)
        runBlocking {
            instance.launch(client)
            client.whileRunning()
            instance.awaitStopped()
        }
        assertOk(client)
        return client
    }

    private fun assertOk(client: GameClient) {
        val text = client.text()
        if ("ok" !in text || text.any { it.startsWith("Script error") }) fail("script output: $text")
    }

    @Test
    fun statusAndTimers() {
        val client = GameClient()
        client.setCurrentTime(Instant.fromEpochSeconds(1000))
        client.roundTimeEnd.value = 1006
        client.indicators.value = setOf("stunned", "kneeling")
        runOk(
            """
            assert(status().stunned and status().kneeling and not status().hidden)
            assert(roundtime() == 6 and casttime() == 0)
            echo("ok")
            """.trimIndent(),
            client = client,
        )
    }

    @Test
    fun protocolLinesStartWithTheHistoryAndFollowTheConnection() {
        val client = GameClient()
        client.protocol("<nav rm='1'/>")
        client.protocol("<prompt time='1'>&gt;</prompt>")
        runOk(
            """
            local lines, lost = protocolLines()
            assert(#lines == 2 and lines[1] == "<nav rm='1'/>" and lost == false)
            assert(#protocolLines() == 0)
            echo("ready")
            assert(waitFor("go", 5))
            lines = protocolLines()
            assert(#lines == 1 and lines[1] == "<pushBold/>go<popBold/>", lines[1])
            echo("ok")
            """.trimIndent(),
            client = client,
        ) {
            awaitPrinted("ready")
            protocol("<pushBold/>go<popBold/>")
            line("go")
        }
        assertTrue(client.taps.isEmpty(), "the tap is removed when the script ends")
    }

    @Test
    fun regularExpressions() {
        runOk(
            """
            assert(re.test("k[aeiou]bold", "a Kobold", "i"))
            assert(not re.test("orc", "kobold"))
            local verb, amount = re.match([[(\w+) (\d+)]], "hit 12")
            assert(verb == "hit" and amount == "12")
            assert(re.match("kobold", "a kobold") == "kobold")
            assert(re.match("orc", "a kobold") == nil)
            local s, e = re.find("bold", "a kobold")
            assert(s == 5 and e == 8)
            local text = "é kobold"
            local s2, e2 = re.find("bold", text)
            assert(text:sub(s2, e2) == "bold")
            assert(re.find("o", "kobold", 3) == 4)
            assert(re.find("o", "kobold", 5) == nil)
            local words = {}
            for w in re.gmatch([[\w+]], "one two three") do words[#words + 1] = w end
            assert(#words == 3 and words[3] == "three")
            assert(re.gsub([[(\d+)]], "a1b22", "<$1>") == "a<1>b<22>")
            local out, n = re.gsub([[\d+]], "a1b22", function(d) return tostring(#d) end)
            assert(out == "a1b2" and n == 2)
            assert(re.gsub([[(\w+)]], "hi there", { hi = "HI" }) == "HI there")
            local parts = re.split([[,\s*]], "a, b,c")
            assert(#parts == 3 and parts[2] == "b")
            local r = re.compile("^hit", "i")
            assert(r:test("Hit it") and not r:test("a hit"))
            assert(re.test(re.escape("a.b"), "a.b") and not re.test(re.escape("a.b"), "axb"))
            local ok, err = pcall(re.compile, "x", "z")
            assert(not ok and err:find("unknown regex flag"))
            echo("ok")
            """.trimIndent(),
        )
    }

    @Test
    fun aReplyThatComesBackAtOnceIsNotMissed() {
        val client = GameClient()
        client.onCommand = { if (it == "look") line("You see nothing unusual.") }
        runOk(
            """
            local line = sendAndWait("look", "nothing unusual", 5)
            assert(line == "You see nothing unusual.", tostring(line))
            echo("ok")
            """.trimIndent(),
            client = client,
        )
    }

    @Test
    fun waitForGivesTheIndexAndCaptures() {
        runOk(
            """
            echo("ready")
            local line, index, captures = waitFor({ "miss", [[hit the (\w+) for (\d+)]] }, 5)
            assert(index == 2 and captures[1] == "kobold" and captures[2] == "12", tostring(line))
            assert(nextLine(5) == "after")
            echo("ok")
            """.trimIndent(),
        ) {
            awaitPrinted("ready")
            line("You hit the kobold for 12 damage")
            line("after")
        }
    }

    @Test
    fun waitsTimeOut() {
        runOk(
            """
            local started = now()
            assert(waitFor("never", 0.3) == nil)
            assert(nextLine(0.2) == nil)
            assert(waitForPrompt(0.2) == nil)
            assert(waitForNav(0.2) == nil)
            assert(waitUntil(function() return false end, 0.3) == nil)
            local count = 0
            assert(waitUntil(function() count = count + 1; return count == 3 and "done" end, 5, 0.05) == "done")
            assert(now() - started >= 1)
            echo("ok")
            """.trimIndent(),
        )
    }

    @Test
    fun clearLinesForgetsWhatCameBefore() {
        runOk(
            """
            echo("ready")
            assert(waitFor("second", 5))
            clearLines()
            echo("cleared")
            assert(nextLine(5) == "third")
            echo("ok")
            """.trimIndent(),
        ) {
            awaitPrinted("ready")
            line("first")
            line("second")
            line("stale")
            awaitPrinted("cleared")
            line("third")
        }
    }

    @Test
    fun captureTakesTheAnswerOffTheScreen() {
        val client = GameClient()
        client.onCommand = {
            if (it == "spell active") {
                line("before")
                line("You currently have:")
                line("Spirit Warding I")
                line("End of list.")
                line("after")
            }
        }
        runOk(
            """
            local lines, complete = capture("spell active", "currently have", [[^End of list]], 5)
            assert(complete and #lines == 3 and lines[2] == "Spirit Warding I")
            assert(nextLine(1) == "before")
            assert(nextLine(1) == "after")
            echo("ok")
            """.trimIndent(),
            client = client,
        )
        assertEquals(listOf("spell active"), client.quietCommands)
        assertTrue("spell active" !in client.sentCommands)
    }

    @Test
    fun captureWithoutPatternsRunsToThePromptOrTheTimeout() {
        val client = GameClient()
        client.onCommand = {
            if (it == "exp") {
                line("Level: 10")
                line("Exp: 5000")
                prompt()
            }
        }
        runOk(
            """
            local lines, complete = capture("exp", nil, nil, 5)
            assert(complete and #lines == 2 and lines[1] == "Level: 10")
            local nothing, done = capture("silence", nil, nil, 0.3)
            assert(#nothing == 0 and done == false)
            echo("ok")
            """.trimIndent(),
            client = client,
        )
    }

    @Test
    fun handlersRunWhileTheScriptWaits() {
        val client = GameClient()
        val instance =
            instance(
                """
                onLine(function(line) echo("heard " .. line) end)
                echo("ready")
                pause(10)
                echo("after")
                """.trimIndent(),
            )
        runBlocking {
            instance.launch(client)
            client.awaitPrinted("ready")
            client.line("a kobold arrives")
            client.awaitPrinted("heard a kobold arrives")
            assertFalse("after" in client.text())
            instance.stop()
            instance.awaitStopped()
        }
    }

    @Test
    fun aHandlerCannotWaitWhileTheScriptWaits() {
        val client = GameClient()
        val instance =
            instance(
                """
                onLine(function(line) pause(1) end)
                echo("ready")
                pause(10)
                """.trimIndent(),
            )
        runBlocking {
            instance.launch(client)
            client.awaitPrinted("ready")
            client.line("anything")
            withTimeout(10.seconds) {
                while (client.text().none { it.contains("a handler cannot wait") }) delay(20.milliseconds)
            }
            instance.stop()
            instance.awaitStopped()
        }
    }

    @Test
    fun requireLoadsLibrariesOnceFromTheScriptFolders() {
        val dir = tempDir("require")
        writeFile(Path(dir, "lib", "greet.lua"), "loads = (loads or 0) + 1\nreturn { hello = function(n) return 'hello ' .. n end }")
        writeFile(Path(dir, "lib", "gs", "status.lua"), "return { name = ... }")
        writeFile(Path(dir, "lib", "cycle1.lua"), "return require('cycle2')")
        writeFile(Path(dir, "lib", "cycle2.lua"), "return require('cycle1')")
        writeFile(Path(dir, "lib", "broken.lua"), "return {")
        runOk(
            """
            local greet = require("greet")
            assert(greet.hello("you") == "hello you")
            assert(require("greet") == greet and loads == 1)
            assert(require("gs.status").name == "gs.status")
            local ok, err = pcall(require, "cycle1")
            assert(not ok and err:find("requires itself"), err)
            ok, err = pcall(require, "../secret")
            assert(not ok and err:find("not a library name"), err)
            ok, err = pcall(require, "nowhere")
            assert(not ok and err:find("no library"), err)
            assert(not pcall(require, "broken"))
            assert(io == nil and os == nil and dofile == nil and loadfile == nil)
            echo("ok")
            """.trimIndent(),
            scriptDirs = listOf(Path(dir, "elsewhere"), dir),
        )
    }

    @Test
    fun scriptsStartRunAndStopOtherScripts() {
        val manager = TimedScriptManager()
        runOk(
            """
            assert(scripts.start("missing") == false)
            assert(scripts.run("helper with args") == true)
            assert(not scripts.isRunning("helper"))
            assert(scripts.start("other"))
            assert(scripts.isRunning("OTHER"))
            assert(scripts.list()[1] == "other")
            assert(scripts.stop("other") and not scripts.isRunning("other"))
            assert(scripts.stop("ghost") == false)
            echo("ok")
            """.trimIndent(),
            scriptManager = manager,
        )
        assertEquals(listOf("other"), manager.stopped)
    }

    @Test
    fun storageKeepsDataBetweenRuns() {
        val dir = tempDir("storage")
        runOk(
            """
            assert(next(storage.load()) == nil)
            storage.save({ hp = 5, list = { "a", "b" }, nested = { flag = true } })
            echo("ok")
            """.trimIndent(),
            storageDir = dir,
        )
        runOk(
            """
            local data = storage.load()
            assert(data.hp == 5 and data.list[2] == "b" and data.nested.flag == true)
            echo("ok")
            """.trimIndent(),
            storageDir = dir,
        )
        assertTrue(SystemFileSystem.exists(Path(dir, "testchar", "test.json")))
    }
}
