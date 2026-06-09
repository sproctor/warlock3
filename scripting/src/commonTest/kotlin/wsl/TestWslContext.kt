package warlockfe.warlock3.scripting.wsl

import kotlinx.coroutines.CoroutineScope
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import warlockfe.warlock3.core.prefs.repositories.VariableRepository

/** Builds a [WslContext] wired entirely to in-memory test doubles. */
fun buildTestContext(
    scope: CoroutineScope,
    lines: List<WslLine> = emptyList(),
    client: FakeWarlockClient = FakeWarlockClient(),
    variableDao: FakeVariableDao = FakeVariableDao(),
    scriptManager: FakeScriptManager = FakeScriptManager(),
    file: Path = Path(SystemTemporaryDirectory, "wsl-test-instance.wsl"),
): WslContext {
    val variableRepository = VariableRepository(variableDao)
    val scriptInstance =
        WslScriptInstance(
            id = 1L,
            name = "test",
            file = file,
            variableRepository = variableRepository,
            highlightRepository = FakeHighlightRepository(),
            nameRepository = FakeNameRepository(),
            scriptManager = scriptManager,
            soundPlayer = FakeSoundPlayer(),
            fileSystem = SystemFileSystem,
        )
    return WslContext(
        client = client,
        scriptManager = scriptManager,
        lines = lines,
        scriptInstance = scriptInstance,
        scope = scope,
        variableRepository = variableRepository,
        highlightRepository = FakeHighlightRepository(),
        nameRepository = FakeNameRepository(),
        commandHandler = { client.sendCommand(it) },
        soundPlayer = FakeSoundPlayer(),
        fileSystem = SystemFileSystem,
    )
}
