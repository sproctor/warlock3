package warlockfe.warlock3.scripting.wsl

import kotlinx.coroutines.CoroutineScope
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import warlockfe.warlock3.core.prefs.config.CharacterConfigStore
import warlockfe.warlock3.core.prefs.repositories.VariableRepository

private var testStoreSeq = 0

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
