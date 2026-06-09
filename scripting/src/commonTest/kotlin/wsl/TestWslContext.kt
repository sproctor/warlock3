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
    soundPlayer: FakeSoundPlayer = FakeSoundPlayer(),
    highlightRepository: FakeHighlightRepository = FakeHighlightRepository(),
    nameRepository: FakeNameRepository = FakeNameRepository(),
    file: Path = Path(SystemTemporaryDirectory, "wsl-test-instance.wsl"),
): WslContext {
    val variableRepository = VariableRepository(variableDao)
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
