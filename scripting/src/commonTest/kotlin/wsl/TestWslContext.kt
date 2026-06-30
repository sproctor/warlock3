package warlockfe.warlock3.scripting.wsl

import kotlinx.coroutines.CoroutineScope
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlinx.io.writeString
import warlockfe.warlock3.core.prefs.config.CharacterConfigStore
import warlockfe.warlock3.core.prefs.repositories.VariableRepository
import warlockfe.warlock3.core.script.ScriptStatus

private var testStoreSeq = 0

/** Parses [content] into script lines via a throwaway temp file, then deletes the file. */
fun parseScript(content: String): List<WslLine> {
    val path = Path(SystemTemporaryDirectory, "wsl-script-${testStoreSeq++}.wsl")
    SystemFileSystem.sink(path).buffered().use { it.writeString(content) }
    try {
        return WslScript("test", path, SystemFileSystem).parse()
    } finally {
        SystemFileSystem.delete(path)
    }
}

/**
 * Drives the context through its script line-by-line the same way [WslScriptInstance] does,
 * stopping when the script runs off the end or a command (e.g. `exit`) stops it. [maxStatements]
 * guards against a runaway loop in a buggy test script.
 */
suspend fun WslContext.runToCompletion(maxStatements: Int = 10_000) {
    var executed = 0
    while (scriptInstance.status != ScriptStatus.Stopped) {
        val line = getNextLine() ?: break
        line.statement.execute(this)
        if (++executed > maxStatements) {
            throw AssertionError("Script exceeded $maxStatements statements without terminating")
        }
    }
}

/** A [CharacterConfigStore] over a throwaway temp directory; backs a real [VariableRepository] in tests. */
fun newTestConfigStore(): CharacterConfigStore =
    CharacterConfigStore(
        Path(SystemTemporaryDirectory, "wsl-test-store-${testStoreSeq++}").toString(),
        SystemFileSystem,
    )

/** Builds a [WslContext] wired entirely to in-memory test doubles. */
fun buildTestContext(
    scope: CoroutineScope,
    lines: List<WslLine> = emptyList(),
    client: FakeWarlockClient = FakeWarlockClient(),
    configStore: CharacterConfigStore = newTestConfigStore(),
    scriptManager: FakeScriptManager = FakeScriptManager(),
    soundPlayer: FakeSoundPlayer = FakeSoundPlayer(),
    highlightRepository: FakeHighlightRepository = FakeHighlightRepository(),
    nameRepository: FakeNameRepository = FakeNameRepository(),
    file: Path = Path(SystemTemporaryDirectory, "wsl-test-instance.wsl"),
): WslContext {
    val variableRepository = VariableRepository(configStore)
    val scriptInstance =
        WslScriptInstance(
            id = 1L,
            name = "test",
            file = file,
            variableRepository = variableRepository,
            highlightRepository = highlightRepository,
            nameRepository = nameRepository,
            scriptManager = scriptManager,
            soundPlayer = soundPlayer,
            fileSystem = SystemFileSystem,
        )
    return WslContext(
        client = client,
        scriptManager = scriptManager,
        lines = lines,
        scriptInstance = scriptInstance,
        scope = scope,
        variableRepository = variableRepository,
        highlightRepository = highlightRepository,
        nameRepository = nameRepository,
        commandHandler = { client.sendCommand(it) },
        soundPlayer = soundPlayer,
        fileSystem = SystemFileSystem,
    )
}
