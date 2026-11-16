package warlockfe.warlock3.compose.ui.game

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import warlockfe.warlock3.compose.deleteTestDir
import warlockfe.warlock3.compose.testAppContainer
import warlockfe.warlock3.core.client.SendCommandType
import warlockfe.warlock3.core.client.WarlockClient
import warlockfe.warlock3.core.script.ScriptData
import warlockfe.warlock3.core.script.ScriptInstance
import warlockfe.warlock3.core.script.ScriptManager
import warlockfe.warlock3.core.script.ScriptStatus
import warlockfe.warlock3.core.text.Alias
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Where a typed line goes, and the client's own `/` commands for the scripts that are running. */
class CommandHandlerTest {
    private lateinit var dir: java.nio.file.Path
    private val job = Job()

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("command-handler-test")
    }

    @AfterTest
    fun tearDown() {
        job.cancel()
        deleteTestDir(dir)
    }

    private class FakeScript(
        override val id: Long,
        override val name: String,
        private val manager: FakeScriptManager,
    ) : ScriptInstance {
        override var status = ScriptStatus.Running

        override fun start(
            client: WarlockClient,
            argumentString: String,
            onStop: () -> Unit,
            commandHandler: suspend (String) -> SendCommandType,
        ) = Unit

        override fun stop() {
            status = ScriptStatus.Stopped
            manager.forget(id)
        }

        override fun suspend() {
            status = ScriptStatus.Suspended
            manager.scriptStateChanged(this)
        }

        override fun resume() {
            status = ScriptStatus.Running
            manager.scriptStateChanged(this)
        }
    }

    /** Starts nothing real: a started script is recorded and listed as running. */
    private class FakeScriptManager : ScriptManager {
        private val scripts = MutableStateFlow<Map<Long, ScriptData>>(emptyMap())
        override val runningScripts: StateFlow<Map<Long, ScriptData>> = scripts

        val started = mutableListOf<String>()
        private var nextId = 1L

        fun run(name: String): FakeScript {
            val script = FakeScript(nextId++, name, this)
            scripts.update { it + (script.id to ScriptData(script.status, script)) }
            return script
        }

        fun forget(id: Long) {
            scripts.update { it - id }
        }

        override suspend fun startScript(
            client: WarlockClient,
            command: String,
            commandHandler: suspend (String) -> SendCommandType,
        ) {
            started += command
            run(command.substringBefore(' '))
        }

        override suspend fun startScript(
            client: WarlockClient,
            file: Path,
            commandHandler: suspend (String) -> SendCommandType,
        ) = throw UnsupportedOperationException()

        override suspend fun startScript(
            client: WarlockClient,
            name: String,
            contents: String,
            commandHandler: suspend (String) -> SendCommandType,
            extension: String,
        ) = throw UnsupportedOperationException()

        override fun findScriptInstance(description: String): ScriptInstance? =
            scripts.value.values
                .map { it.instance }
                // By id, or by a prefix of the name, as the real manager finds them.
                .firstOrNull { it.id.toString() == description || it.name.startsWith(description, ignoreCase = true) }

        override fun scriptStateChanged(instance: ScriptInstance) {
            scripts.update { it + (instance.id to ScriptData(instance.status, instance)) }
        }
    }

    private val scriptManager = FakeScriptManager()
    private val aliases = MutableStateFlow<List<Alias>>(emptyList())
    private lateinit var client: FakeWarlockClient

    private suspend fun TestScope.handler(): CommandHandler {
        client = FakeWarlockClient(testAppContainer(dir, StandardTestDispatcher(testScheduler)).windowRegistryFactory.create())
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + job)
        val control = ScriptControl(client, scriptManager, scope)
        return CommandHandler(client, scriptManager, control, aliases, MutableStateFlow("."))
    }

    @Test
    fun aPlainLineGoesToTheGameWithItsAliasesApplied() =
        runTest {
            val handler = handler()
            aliases.value = listOf(Alias(pattern = "^att\\b", replacement = "attack target"))

            assertEquals(SendCommandType.COMMAND, handler.handle("att"))

            assertEquals(listOf("attack target"), client.sentCommands)
        }

    @Test
    fun theScriptPrefixStartsAScript() =
        runTest {
            val handler = handler()

            assertEquals(SendCommandType.SCRIPT, handler.handle(".hunt goblin"))

            assertEquals(listOf("hunt goblin"), scriptManager.started)
            assertEquals(emptyList(), client.sentCommands)
            // Echoed, so the user sees what they ran.
            assertEquals(listOf(".hunt goblin"), client.printed)
        }

    @Test
    fun killPauseAndResumeActOnEachNamedScript() =
        runTest {
            val handler = handler()
            val hunt = scriptManager.run("hunt")
            val forage = scriptManager.run("forage")

            handler.handle("/pause hunt ${forage.id}")
            assertEquals(ScriptStatus.Suspended, hunt.status)
            assertEquals(ScriptStatus.Suspended, forage.status)

            handler.handle("/resume hunt")
            assertEquals(ScriptStatus.Running, hunt.status)
            assertEquals(ScriptStatus.Suspended, forage.status)

            assertEquals(SendCommandType.ACTION, handler.handle("/kill hunt missing"))
            assertEquals(ScriptStatus.Stopped, hunt.status)
            assertTrue("Script hunt stopped." in client.printed)
            assertTrue("Script missing not found." in client.printed)
            assertEquals(emptyList(), client.sentCommands)
        }

    @Test
    fun extraSpacesBetweenScriptNamesStopNoOtherScript() =
        runTest {
            val handler = handler()
            val hunt = scriptManager.run("hunt")
            val forage = scriptManager.run("forage")
            val gather = scriptManager.run("gather")

            handler.handle("/kill hunt  gather")

            assertEquals(ScriptStatus.Stopped, hunt.status)
            assertEquals(ScriptStatus.Stopped, gather.status)
            // An empty name between the spaces would have matched the first one still running.
            assertEquals(ScriptStatus.Running, forage.status)
        }

    @Test
    fun listNamesEachRunningScriptWithItsId() =
        runTest {
            val handler = handler()
            handler.handle("/list")
            assertEquals("No scripts are running", client.printed.last())

            val hunt = scriptManager.run("hunt")
            handler.handle("/list")

            assertEquals(listOf("Running scripts:", "hunt - ${hunt.id}"), client.printed.takeLast(2))
        }

    @Test
    fun theOtherClientCommands() =
        runTest {
            val handler = handler()

            handler.handle("/send raw line")
            handler.handle("/dc")
            handler.handle("/nonsense")

            assertEquals(listOf("raw line"), client.directCommands)
            assertTrue(client.disconnectRequested)
            assertEquals("Invalid command.", client.printed.last())
        }
}
