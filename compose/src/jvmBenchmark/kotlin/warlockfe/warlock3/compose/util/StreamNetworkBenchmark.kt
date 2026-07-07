package warlockfe.warlock3.compose.util

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.SystemFileSystem
import warlockfe.warlock3.compose.model.LiteralHighlight
import warlockfe.warlock3.compose.model.ViewHighlight
import warlockfe.warlock3.compose.ui.window.ComposeDialogState
import warlockfe.warlock3.compose.ui.window.ComposeTextStream
import warlockfe.warlock3.compose.ui.window.StreamProfiling
import warlockfe.warlock3.compose.ui.window.StreamWorkQueue
import warlockfe.warlock3.core.client.ClientTextEvent
import warlockfe.warlock3.core.prefs.config.ClientConfigStore
import warlockfe.warlock3.core.prefs.dao.ClientSettingDao
import warlockfe.warlock3.core.prefs.models.ClientSettingEntity
import warlockfe.warlock3.core.prefs.repositories.CharacterRepository
import warlockfe.warlock3.core.prefs.repositories.ClientSettingRepository
import warlockfe.warlock3.core.prefs.repositories.LoggingRepository
import warlockfe.warlock3.core.text.StyleDefinition
import warlockfe.warlock3.core.text.StyledString
import warlockfe.warlock3.core.text.WarlockColor
import warlockfe.warlock3.core.util.SoundPlayer
import warlockfe.warlock3.core.util.WarlockDirs
import warlockfe.warlock3.core.window.DialogState
import warlockfe.warlock3.core.window.TextStream
import warlockfe.warlock3.core.window.WindowRegistry
import warlockfe.warlock3.wrayth.network.NetworkSocket
import warlockfe.warlock3.wrayth.network.WraythClient
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.locks.LockSupport
import kotlin.time.TimeSource

/**
 * End-to-end benchmark for the multi-window / multi-connection lag the way a user actually hits it:
 * over the network. A loopback TCP server replays game protocol into a real [NetworkSocket] -> real
 * [WraythClient] (socket read + ANTLR protocol parse + event dispatch) -> a lean [WindowRegistry] of
 * real [ComposeTextStream]s on a real [StreamWorkQueue], with a dialed-in highlight index. This
 * exercises the whole pipeline the prior in-process line-feed benchmark skipped (which was
 * producer-bound and never touched the socket or the parser).
 *
 * Run, with parity to the desktop app's diagnostic flags:
 *
 *   ./gradlew :compose:streamNetworkBenchmark \
 *       -Pconnections=4 -Pwindows=6 -Plines=40000 -Phighlights=975 \
 *       [-PlinesPerSec=4000] [-Plog=/path/to/capture.log] [-PgcLog] [-Pzgc]
 *
 * Knobs (all optional, with the defaults below):
 *   connections  simultaneous game connections                                  (2)
 *   windows      distinct stream windows each connection routes text across      (6)
 *   lines        synthetic protocol lines streamed per connection                (40000)
 *   highlights   size of the shared highlight index (the real profile is ~975)   (975)
 *   linesPerSec  server send pacing per connection; 0/absent = max-speed firehose (0)
 *   scripts      script-style eventFlow subscribers per conn (regex per text line) (0)
 *   uiCostMicros per-text-event CPU cost on a shared single UI thread, modelling   (0)
 *                a busy Main thread the GameViewModel collector contends for
 *   log          replay this real capture instead of synthetic lines (per conn)  (none)
 *
 * [StreamProfiling] is enabled, so its per-connection queue summaries (queue-wait, exec, peak depth,
 * per-op breakdown) and immediate STALL lines print during the run; the harness prints the headline
 * end-to-end timing at the end.
 */

private data class BenchConfig(
    val connections: Int,
    val windows: Int,
    val lines: Int,
    val highlights: Int,
    val linesPerSec: Int,
    val scripts: Int,
    val uiCostMicros: Long,
    val logPath: String?,
)

private fun intProp(
    name: String,
    default: Int,
): Int = System.getProperty(name)?.trim()?.toIntOrNull() ?: default

fun main() {
    val config =
        BenchConfig(
            connections = intProp("connections", 2),
            windows = intProp("windows", 6),
            lines = intProp("lines", 40_000),
            highlights = intProp("highlights", 975),
            linesPerSec = intProp("linesPerSec", 0),
            scripts = intProp("scripts", 0),
            uiCostMicros = System.getProperty("uiCostMicros")?.trim()?.toLongOrNull() ?: 0L,
            logPath = System.getProperty("log")?.takeIf { it.isNotBlank() },
        )
    StreamProfiling.enabled = true
    runBlocking { runBenchmark(config) }
}

private suspend fun runBenchmark(config: BenchConfig) {
    // Lines a single connection streams: a real capture (replayed to every connection) or synthetic.
    // Read latin-1 (byte-preserving) so the exact on-disk bytes reach NetworkSocket, which decodes
    // them as Windows-1252 like the live game connection; the default UTF-8 read would mangle any
    // 0x80-0xFF byte before it ever hit the real decoder.
    val realLog: List<String>? = config.logPath?.let { File(it).readLines(Charsets.ISO_8859_1) }

    // One shared highlight index, sized to the real-world profile, the way the app shares one index
    // across a character's windows. Built once here rather than per line on the render path.
    val highlightIndex = MutableStateFlow(HighlightIndex(buildHighlights(config.highlights)))
    val presets = MutableStateFlow(emptyMap<String, StyleDefinition>())

    // A minimal-but-real repository stack for WraythClient. Backed by a throwaway temp config dir and
    // an in-memory settings DAO; logging is forced off (LogType.NONE) so the hot path never touches
    // the filesystem. No SQLite, no DB.
    val tempDir =
        java.nio.file.Files
            .createTempDirectory("warlock-bench")
            .toString()
    val clientConfigStore = ClientConfigStore(tempDir, SystemFileSystem)
    clientConfigStore.mutateClient { it.copy(logType = "NONE") }
    val warlockDirs = WarlockDirs(homeDir = tempDir, dataDir = tempDir, configDir = tempDir, logDir = tempDir)
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val clientSettings = ClientSettingRepository(InMemoryClientSettingDao(), clientConfigStore, warlockDirs)
    val loggingRepository = LoggingRepository(clientSettings, appScope)
    val characterRepository = CharacterRepository(clientConfigStore)

    // A single shared thread modelling the desktop UI thread: every connection's GameViewModel-style
    // eventFlow collector runs here, so the parse loops contend for it exactly as they do for the real
    // Main thread. uiCostMicros makes that collector burn CPU per text event (modelling recomposition).
    // Measured finding: even a deliberately slow shared UI collector does NOT throttle the parsers, so
    // the zero-buffer eventFlow is not the per-line rendezvous bottleneck it looks like on paper; emit
    // does not gate the parse loop on its subscribers.
    val uiExecutor = Executors.newSingleThreadExecutor { Thread(it, "bench-ui").apply { isDaemon = true } }
    val uiDispatcher = uiExecutor.asCoroutineDispatcher()

    // Loopback replay server: one accepted socket per connection, each streaming a private copy of the
    // line list (after consuming the client's two-line handshake), optionally paced.
    val server = ReplayServer(linesPerSec = config.linesPerSec)
    server.start()

    println(
        "stream-network-benchmark: connections=${config.connections} windows=${config.windows} " +
            "highlights=${config.highlights} " +
            (if (realLog != null) "log=${config.logPath} (${realLog.size} lines)" else "lines=${config.lines} (synthetic)") +
            " pacing=" + (if (config.linesPerSec > 0) "${config.linesPerSec}/s" else "firehose") +
            " scripts=${config.scripts} uiCostMicros=${config.uiCostMicros} port=${server.port}",
    )

    val started = TimeSource.Monotonic.markNow()
    var totalLines = 0L

    val results =
        (0 until config.connections)
            .map { index ->
                val lines = realLog ?: syntheticLines(config.lines, config.windows, index)
                totalLines += lines.size
                server.enqueue(index, lines)
                appScope.async {
                    runConnection(
                        index = index,
                        port = server.port,
                        highlightIndex = highlightIndex,
                        presets = presets,
                        characterRepository = characterRepository,
                        loggingRepository = loggingRepository,
                        scripts = config.scripts,
                        uiDispatcher = uiDispatcher,
                        uiCostMicros = config.uiCostMicros,
                    )
                }
            }.awaitAll()

    val elapsed = started.elapsedNow()
    server.stop()
    appScope.cancel()
    uiExecutor.shutdownNow()

    println("----- stream-network-benchmark results -----")
    results.sortedBy { it.index }.forEach { r ->
        println(
            "conn ${r.index} [${r.tag}]: ${r.lines} lines, ${r.textEvents} text events, " +
                "${r.recompositions} recompositions, finished in ${r.elapsed}",
        )
    }
    val seconds = elapsed.inWholeMicroseconds / 1_000_000.0
    val rate = if (seconds > 0) (totalLines / seconds).toLong() else 0
    println(
        "TOTAL: $totalLines lines across ${config.connections} connections in $elapsed " +
            "($rate lines/sec aggregate)",
    )
}

private data class ConnectionResult(
    val index: Int,
    val tag: String,
    val lines: Int,
    val textEvents: Long,
    val recompositions: Long,
    val elapsed: kotlin.time.Duration,
)

private suspend fun runConnection(
    index: Int,
    port: Int,
    highlightIndex: StateFlow<HighlightIndex>,
    presets: StateFlow<Map<String, StyleDefinition>>,
    characterRepository: CharacterRepository,
    loggingRepository: LoggingRepository,
    scripts: Int,
    uiDispatcher: CoroutineDispatcher,
    uiCostMicros: Long,
): ConnectionResult {
    val ioDispatcher = Dispatchers.IO
    val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    val workQueue = StreamWorkQueue(scope)
    val registry =
        BenchWindowRegistry(
            scope = scope,
            workQueue = workQueue,
            highlights = highlightIndex,
            presets = presets,
            uiDispatcher = uiDispatcher,
            uiCostMicros = uiCostMicros,
        )
    val socket = NetworkSocket(ioDispatcher)
    socket.connect("127.0.0.1", port)
    val client =
        WraythClient(
            characterRepository = characterRepository,
            windowRegistry = registry,
            fileLogging = loggingRepository,
            ioDispatcher = ioDispatcher,
            socket = socket,
        )

    // Representative eventFlow subscribers. The always-on GameViewModel-style collector runs on the
    // shared UI thread and (like the real one) does no work for text events, but still has to RECEIVE
    // each one there; that handoff is what couples the parse loop to the UI thread's availability. The
    // UI thread's actual busyness is modelled on the publish/recomposition path (see BenchWindow
    // Registry), so when recomposition is heavy this collector is starved and the parser stalls. The
    // `scripts` collectors run a trigger set against every ClientTextEvent, as WslContext does.
    // onSubscription gates connect so all are registered before the first event.
    val textEvents =
        java.util.concurrent.atomic
            .AtomicLong()
    val subscribed = mutableListOf<CompletableDeferred<Unit>>()
    val gameViewReady = CompletableDeferred<Unit>()
    subscribed.add(gameViewReady)
    scope.launch(uiDispatcher) {
        client.eventFlow.onSubscription { gameViewReady.complete(Unit) }.collect { event ->
            if (event is ClientTextEvent) textEvents.incrementAndGet()
        }
    }
    repeat(scripts) {
        val ready = CompletableDeferred<Unit>()
        subscribed.add(ready)
        scope.launch {
            client.eventFlow.onSubscription { ready.complete(Unit) }.collect { event ->
                if (event is ClientTextEvent) {
                    for (trigger in SCRIPT_TRIGGERS) trigger.containsMatchIn(event.text)
                }
            }
        }
    }
    subscribed.forEach { it.await() }

    val started = TimeSource.Monotonic.markNow()
    client.connect("benchmark-key-$index")

    // The read loop ends (and flips disconnected) when the server closes the socket at end of stream.
    client.disconnected.first { it }
    // The socket is drained and parsed, but the produced stream ops may still be in flight; wait for
    // the queue to publish them so the timing covers the full render pipeline, not just the parse.
    workQueue.awaitFlushed()
    val elapsed = started.elapsedNow()

    val lineCount = registry.getStreams().sumOf { (it as ComposeTextStream).lines.value.size }
    val tag = "dr:bench$index"
    val recompositions = registry.recompositionCount()
    registry.close()
    return ConnectionResult(
        index = index,
        tag = tag,
        lines = lineCount,
        textEvents = textEvents.get(),
        recompositions = recompositions,
        elapsed = elapsed,
    )
}

/**
 * A lean [WindowRegistry] that builds real [ComposeTextStream]s sharing one [StreamWorkQueue], with
 * constant highlight/name/alteration/preset flows supplied directly. Mirrors the production registry's
 * batched [updateComponent] broadcast (one op touching every stream) so the multi-window component
 * amplification is exercised, without the production registry's DB/file-backed repositories.
 */
private class BenchWindowRegistry(
    private val scope: CoroutineScope,
    private val workQueue: StreamWorkQueue,
    private val highlights: StateFlow<HighlightIndex>,
    override val presets: StateFlow<Map<String, StyleDefinition>>,
    private val uiDispatcher: CoroutineDispatcher,
    private val uiCostMicros: Long,
) : WindowRegistry {
    private val names = MutableStateFlow<List<ViewHighlight>>(emptyList())
    private val alterations = MutableStateFlow<List<warlockfe.warlock3.wrayth.util.CompiledAlteration>>(emptyList())
    private val monoFont = MutableStateFlow<warlockfe.warlock3.core.text.FontConfig?>(null)
    private val streams = ConcurrentHashMap<String, ComposeTextStream>()
    private val dialogs = ConcurrentHashMap<String, ComposeDialogState>()
    private val recompositions =
        java.util.concurrent.atomic
            .AtomicLong()

    // Observed lines-snapshot changes across all windows = recompositions the shared UI thread did.
    // It is a conflated count (a StateFlow collector only sees the latest value), so it already reflects
    // the UI coalescing that StateFlow/Compose do on top of the work queue's per-batch publish coalescing.
    fun recompositionCount(): Long = recompositions.get()

    // computeIfAbsent (not Kotlin's getOrPut) so the builder runs at most once per name even under
    // concurrent calls; a duplicate ComposeTextStream would leak the flow collectors its init launches.
    override fun getOrCreateStream(name: String): TextStream =
        streams.computeIfAbsent(name) {
            val stream =
                ComposeTextStream(
                    id = name,
                    maxLines = ClientSettingRepository.DEFAULT_MAX_SCROLL_LINES,
                    markLinks = false,
                    showImages = true,
                    showTimestamps = false,
                    suppressPrompts = false,
                    highlights = highlights,
                    names = names,
                    alterations = alterations,
                    presets = presets,
                    monoFont = monoFont,
                    soundPlayer = SilentSoundPlayer,
                    workQueue = workQueue,
                    scope = scope,
                )
            // Model the window's UI: each observed lines snapshot is one recomposition on the single
            // shared UI thread, costing uiCostMicros. All windows of all connections share that thread,
            // so this is where UI-thread load (and the parser stalls it induces via the eventFlow
            // rendezvous) actually comes from.
            scope.launch(uiDispatcher) {
                stream.lines.drop(1).collect {
                    recompositions.incrementAndGet()
                    if (uiCostMicros > 0) busySpinMicros(uiCostMicros)
                }
            }
            stream
        }

    override fun getStreams(): Collection<TextStream> = streams.values

    override suspend fun updateComponent(
        name: String,
        value: StyledString,
    ) {
        workQueue.submit("component") {
            streams.values.forEach { it.updateComponentSync(name, value) }
        }
    }

    override fun getOrCreateDialog(name: String): DialogState = dialogs.computeIfAbsent(name) { ComposeDialogState(name) }

    override fun setCharacterId(characterId: String) {
        workQueue.tag = characterId
    }

    override fun close() {
        scope.cancel()
    }
}

private object SilentSoundPlayer : SoundPlayer {
    override suspend fun playSound(filename: String): String? = null
}

/** In-memory [ClientSettingDao] so the settings repository needs no SQLite. */
private class InMemoryClientSettingDao : ClientSettingDao {
    private val values = ConcurrentHashMap<String, String>()
    private val flows = ConcurrentHashMap<String, MutableStateFlow<String?>>()

    override suspend fun getAll(): List<ClientSettingEntity> = values.map { ClientSettingEntity(it.key, it.value) }

    override suspend fun getByKey(key: String): String? = values[key]

    override fun observeByKey(key: String): kotlinx.coroutines.flow.Flow<String?> = flows.getOrPut(key) { MutableStateFlow(values[key]) }

    override suspend fun removeByKey(key: String) {
        values.remove(key)
        flows[key]?.value = null
    }

    override suspend fun save(entity: ClientSettingEntity) {
        val value = entity.value
        if (value == null) values.remove(entity.key) else values[entity.key] = value
        flows.getOrPut(entity.key) { MutableStateFlow(value) }.value = value
    }
}

/**
 * Loopback TCP server that replays queued line lists. Accepts one socket per [enqueue]d list, reads
 * the client's two handshake lines, then writes the lines (CRLF-terminated, optionally paced) and
 * closes the socket to signal end of stream. Blocking sockets on daemon threads so they never hold
 * the JVM open.
 */
private class ReplayServer(
    private val linesPerSec: Int,
) {
    private val serverSocket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
    private val byIndex = ConcurrentHashMap<Int, List<String>>()
    private val workers = Executors.newCachedThreadPool { r -> Thread(r, "replay-worker").apply { isDaemon = true } }
    private val acceptThread = Thread({ acceptLoop() }, "replay-accept").apply { isDaemon = true }
    private val live = CopyOnWriteArrayList<java.net.Socket>()

    val port: Int get() = serverSocket.localPort

    fun enqueue(
        index: Int,
        lines: List<String>,
    ) {
        byIndex[index] = lines
    }

    fun start() {
        acceptThread.start()
    }

    private fun acceptLoop() {
        try {
            while (!serverSocket.isClosed) {
                val socket = serverSocket.accept()
                live.add(socket)
                workers.submit { serve(socket) }
            }
        } catch (_: Exception) {
            // Socket closed during stop(); exit the loop.
        }
    }

    private fun serve(socket: java.net.Socket) {
        try {
            socket.tcpNoDelay = true
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.ISO_8859_1))
            // The client's first handshake line is its key, "benchmark-key-<index>". Pair the socket
            // with that connection's line list by index, not by accept order (which is racy). The
            // second line is the "/FE:WRAYTH ..." info.
            val keyLine = reader.readLine()
            reader.readLine()
            val index = keyLine?.substringAfterLast('-')?.toIntOrNull()
            val lines = index?.let { byIndex[it] } ?: return
            val out = socket.getOutputStream()
            val delayNanos = if (linesPerSec > 0) 1_000_000_000L / linesPerSec else 0L
            for (line in lines) {
                out.write(line.toByteArray(Charsets.ISO_8859_1))
                out.write(CRLF)
                if (delayNanos > 0) {
                    out.flush()
                    LockSupport.parkNanos(delayNanos)
                }
            }
            out.flush()
        } catch (_: Exception) {
            // Connection torn down; nothing to do.
        } finally {
            runCatching { socket.close() }
        }
    }

    fun stop() {
        runCatching { serverSocket.close() }
        live.forEach { runCatching { it.close() } }
        workers.shutdownNow()
    }

    private companion object {
        val CRLF = byteArrayOf('\r'.code.toByte(), '\n'.code.toByte())
    }
}

// Burn CPU for [micros] microseconds. Models the UI thread being busy with recomposition; a busy-spin
// rather than park/sleep because these run on coroutine dispatcher threads (where park is unreliable)
// and recomposition is CPU-bound work anyway.
private fun busySpinMicros(micros: Long) {
    val deadline = System.nanoTime() + micros * 1_000
    while (System.nanoTime() < deadline) { /* busy */ }
}

// A representative script trigger set: the patterns a running script matches against every line of
// game text. Modeled on common hunting/utility scripts (combat outcomes, room cues, social text).
// Each script-style eventFlow subscriber runs all of these against every ClientTextEvent.
private val SCRIPT_TRIGGERS: List<Regex> =
    listOf(
        "You attack",
        "staggers",
        "Obvious exits",
        "Also here",
        "You see",
        "Roundtime",
        "swings? at you",
        "falls to the ground",
        "is dead",
        "You hit",
        "misses? you",
        "You feel fully",
        "loses? its footing",
        "stands? back up",
        "begins? to",
        "glances? at you",
        "nods?",
        "smiles?",
        "whispers?",
        "shouts?",
    ).map { Regex(it, RegexOption.IGNORE_CASE) }

// A pool of Capitalized proper names so the case-sensitive name-highlight match path does real work
// per line (lowercasing a Capitalized needle is the per-line cost the highlight index has to handle).
private val NAMES = listOf("Zarnok", "Vexil", "Quorth", "Kobold", "Goblin", "Troll", "Mordrin", "Sable", "Drake", "Quorth")

// A realistic highlight profile: mostly literal, whole-word, case-sensitive Capitalized proper names.
// The first few are names that actually appear in the streamed lines (so they match); the rest are
// filler so the index is the size the real app carries (~975), making per-line matching do real work.
private fun buildHighlights(count: Int): List<ViewHighlight> =
    (0 until count).map { i ->
        LiteralHighlight(
            literal = if (i < NAMES.size) NAMES[i] else "Hlword$i",
            matchPartialWord = false,
            ignoreCase = false,
            style = StyleDefinition(bold = true, textColor = WarlockColor(red = 255, green = 200, blue = 0)),
            sound = null,
        )
    }

/**
 * Representative SGE protocol for one connection: an [app] line to set the character (which also sets
 * the log name and routes highlights), then a mix of room/combat/text lines routed across [windows]
 * stream windows and periodic component updates (which broadcast to every window). Each string is a
 * complete protocol line. Content varies by index so highlight matching and parsing aren't hitting a
 * single cached shape.
 */
private fun syntheticLines(
    count: Int,
    windows: Int,
    connIndex: Int,
): List<String> {
    val out = ArrayList<String>(count + 1)
    out.add("<app char=\"Bench$connIndex\" game=\"dr\"/>")
    for (i in 0 until count) {
        val w = i % windows
        val name = NAMES[i % NAMES.size]
        val name2 = NAMES[(i / NAMES.size) % NAMES.size]
        out.add(
            when (i % 6) {
                0 -> {
                    "<pushStream id=\"win$w\"/><style id=\"roomName\"/>[Chamber $i]<style id=\"\"/>" +
                        " Obvious exits: north, east, down. Also here: $name, $name2.<popStream/>"
                }

                1 -> {
                    // Bold is opened and closed on the same line, as the real protocol does: an
                    // unbalanced push would pile up on WraythClient's style stack and be re-applied to
                    // every later line (a quadratic blowup that is a malformed-input artifact).
                    "<pushBold/>You attack the <a exist=\"$i\" noun=\"orc\">orc</a> and " +
                        "<preset id=\"speech\">$name staggers back</preset>!<popBold/>"
                }

                2 -> {
                    "<pushStream id=\"win$w\"/>You see a tall orc warrior standing guard by the gate, gripping a rusty halberd. ($i)<popStream/>"
                }

                3 -> {
                    "<component id=\"room desc\">A wide cobbled plaza with a $name statue ($i).</component>"
                }

                4 -> {
                    "<style id=\"roomName\"/>[Town Square $i]<style id=\"\"/> A $name merchant nods as you pass."
                }

                else -> {
                    "<component id=\"room objs\">a leather backpack, a $name idol, and a rusty halberd ($i)</component>"
                }
            },
        )
        // A prompt every few lines, as the server sends after each push. Besides being realistic, a
        // prompt clears the style stack, so transient styling can't leak across the whole session.
        if (i % 8 == 7) out.add("<prompt>&gt;</prompt>")
    }
    return out
}
