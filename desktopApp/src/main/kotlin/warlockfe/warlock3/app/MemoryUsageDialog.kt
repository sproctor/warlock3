package warlockfe.warlock3.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import co.touchlab.kermit.Logger
import com.sun.management.HotSpotDiagnosticMXBean
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import io.github.vinceglb.filekit.dialogs.compose.rememberFileSaverLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.jewel.ui.component.Text
import warlockfe.warlock3.compose.desktop.shim.WarlockButton
import warlockfe.warlock3.compose.desktop.shim.WarlockDialog
import warlockfe.warlock3.compose.desktop.shim.WarlockOutlinedButton
import warlockfe.warlock3.compose.desktop.shim.WarlockScrollableColumn
import warlockfe.warlock3.compose.model.GameScreen
import warlockfe.warlock3.compose.model.GameState
import warlockfe.warlock3.compose.util.clipEntryOf
import warlockfe.warlock3.compose.util.createPlatformDialogSettings
import warlockfe.warlock3.compose.util.rememberSafeClipboard
import warlockfe.warlock3.core.window.WindowMemoryUsage
import java.io.File
import java.lang.management.ManagementFactory
import kotlin.time.Duration.Companion.seconds

/**
 * Where the app's memory is going, in the app's own terms: how much scrollback each game window is
 * holding, and what else each connection is retaining.
 *
 * Aimed at a user reporting growth rather than at a profiler. The counts are exact and the sizes are
 * estimates (see [warlockfe.warlock3.core.window.MemoryEstimate]), which is enough to point at the
 * window or connection responsible; "save heap dump" is there for when it isn't.
 */
@Suppress("ktlint:compose:modifier-missing-check")
@Composable
fun MemoryUsageDialog(
    games: List<GameState>,
    onCloseRequest: () -> Unit,
) {
    val logger = remember { Logger.withTag("MemoryUsageDialog") }
    var report: MemoryReport? by remember { mutableStateOf(null) }
    var status: String? by remember { mutableStateOf(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun refresh() {
        report = collectReport(games)
    }

    LaunchedEffect(Unit) { refresh() }

    val heapDumpSaveLauncher =
        rememberFileSaverLauncher(
            dialogSettings = FileKitDialogSettings.createPlatformDialogSettings("Save heap dump"),
        ) { file ->
            val target = file?.file ?: return@rememberFileSaverLauncher
            busy = true
            status = "Writing heap dump..."
            scope.launch {
                status =
                    try {
                        val size = withContext(Dispatchers.IO) { dumpHeap(target) }
                        "Wrote ${formatBytes(size)} to ${target.absolutePath}"
                    } catch (e: Exception) {
                        ensureActive()
                        logger.e(e) { "Heap dump failed" }
                        "Heap dump failed: ${e.message}"
                    }
                busy = false
            }
        }

    WarlockDialog(
        title = "Memory usage",
        onCloseRequest = onCloseRequest,
        width = 760.dp,
        height = 600.dp,
    ) {
        // The dialog's own clipboard, which WarlockDialog has already made safe to write to.
        val clipboard = rememberSafeClipboard()
        Column(Modifier.fillMaxSize()) {
            val snapshot = report
            if (snapshot == null) {
                Text("Measuring...")
            } else {
                Text(
                    "Heap: ${formatBytes(snapshot.heapUsedBytes)} used of " +
                        "${formatBytes(snapshot.heapMaxBytes)} max",
                )
                Text(
                    "Most of what Warlock holds is window scrollback. Line counts are exact; sizes are " +
                        "estimates, useful for comparing windows rather than as heap figures.",
                )
                Spacer(Modifier.height(12.dp))
                WarlockScrollableColumn(Modifier.weight(1f)) {
                    snapshot.connections.forEach { connection ->
                        ConnectionSection(connection)
                        Spacer(Modifier.height(16.dp))
                    }
                    if (snapshot.connections.isEmpty()) {
                        Text("No connections are open.")
                    }
                }
            }
            status?.let {
                Spacer(Modifier.height(8.dp))
                Text(it)
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                WarlockOutlinedButton(
                    onClick = {
                        scope.launch {
                            busy = true
                            refresh()
                            busy = false
                        }
                    },
                    text = "Refresh",
                    enabled = !busy,
                )
                WarlockOutlinedButton(
                    onClick = {
                        val text = report?.toText() ?: return@WarlockOutlinedButton
                        // Through the window's clipboard rather than AWT's: a busy system clipboard
                        // used to throw out of here straight into the uncaught handler. Says so only
                        // when the write actually landed, since the retries can run out.
                        scope.launch {
                            val entry = clipEntryOf(text)
                            status =
                                if (entry != null && clipboard.trySetClipEntry(entry)) {
                                    "Report copied to the clipboard."
                                } else {
                                    "Could not copy the report to the clipboard."
                                }
                        }
                    },
                    text = "Copy report",
                    enabled = report != null && !busy,
                )
                WarlockOutlinedButton(
                    onClick = {
                        heapDumpSaveLauncher.launch(suggestedName = "warlock", defaultExtension = "hprof")
                    },
                    text = "Save heap dump...",
                    enabled = !busy,
                )
                WarlockButton(onClick = onCloseRequest, text = "Close")
            }
        }
    }
}

@Composable
private fun ConnectionSection(
    connection: ConnectionMemoryReport,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(connection.title)
        Spacer(Modifier.height(4.dp))
        if (connection.usage.streams.isEmpty()) {
            // Nothing is connected in this window, so there is no scrollback to account for.
            Text("No game windows.")
        } else {
            UsageRow(
                window = "Window",
                shown = "Shown",
                buffered = "Buffered",
                held = "Held",
                componentRefs = "Component refs",
                size = "Est. size",
            )
            connection.usage.streams
                .sortedByDescending { it.estimatedBytes }
                .forEach { stream ->
                    UsageRow(
                        window = stream.streamId,
                        shown = stream.shownLines.toString(),
                        buffered = "${stream.bufferedLines} / ${stream.maxLines}",
                        held = stream.heldLines.toString(),
                        componentRefs = formatCount(stream.componentReferences),
                        size = formatBytes(stream.estimatedBytes),
                    )
                }
            UsageRow(
                window = "Total",
                shown = "",
                buffered = "",
                held = "",
                componentRefs = formatCount(connection.usage.streams.sumOf { it.componentReferences }),
                size = formatBytes(connection.usage.estimatedBytes),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Windows: ${connection.usage.streams.size}   " +
                "Panels: ${connection.usage.panelCount}   " +
                "Running scripts: ${connection.runningScripts}",
        )
    }
}

@Composable
private fun UsageRow(
    window: String,
    shown: String,
    buffered: String,
    held: String,
    componentRefs: String,
    size: String,
) {
    Row(Modifier.fillMaxWidth()) {
        Text(window, modifier = Modifier.width(180.dp), fontFamily = FontFamily.Monospace)
        Text(shown, modifier = Modifier.width(80.dp), fontFamily = FontFamily.Monospace)
        Text(buffered, modifier = Modifier.width(120.dp), fontFamily = FontFamily.Monospace)
        Text(held, modifier = Modifier.width(80.dp), fontFamily = FontFamily.Monospace)
        Text(componentRefs, modifier = Modifier.width(130.dp), fontFamily = FontFamily.Monospace)
        Text(size, fontFamily = FontFamily.Monospace)
    }
}

private data class MemoryReport(
    val heapUsedBytes: Long,
    val heapMaxBytes: Long,
    val connections: List<ConnectionMemoryReport>,
) {
    // The plain-text form of what the dialog shows, for pasting into a bug report.
    fun toText(): String =
        buildString {
            appendLine("Warlock memory usage")
            appendLine("Heap: ${formatBytes(heapUsedBytes)} used of ${formatBytes(heapMaxBytes)} max")
            connections.forEach { connection ->
                appendLine()
                appendLine(connection.title)
                appendLine("  window / shown / buffered / max / held / component refs / est. size")
                connection.usage.streams
                    .sortedByDescending { it.estimatedBytes }
                    .forEach { stream ->
                        appendLine(
                            "  ${stream.streamId} / ${stream.shownLines} / ${stream.bufferedLines} / " +
                                "${stream.maxLines} / ${stream.heldLines} / ${stream.componentReferences} / " +
                                formatBytes(stream.estimatedBytes),
                        )
                    }
                appendLine(
                    "  windows=${connection.usage.streams.size} panels=${connection.usage.panelCount} " +
                        "scripts=${connection.runningScripts} " +
                        "total=${formatBytes(connection.usage.estimatedBytes)}",
                )
            }
        }
}

private data class ConnectionMemoryReport(
    val title: String,
    val usage: WindowMemoryUsage,
    val runningScripts: Int,
)

private suspend fun collectReport(games: List<GameState>): MemoryReport {
    val heap = ManagementFactory.getMemoryMXBean().heapMemoryUsage
    // Snapshot first: games is the live window list, and it can gain or lose an entry while the
    // report is being gathered.
    val snapshot = games.toList()
    // One coroutine per connection. Each has its own work queue, so a connection that never answers
    // costs its own timeout rather than delaying every connection behind it. Within a connection the
    // streams still report one at a time - they share a queue, so there is nothing to overlap.
    val connections =
        coroutineScope {
            snapshot
                .mapIndexed { index, gameState ->
                    async { collectConnectionReport(index, gameState) }
                }.awaitAll()
        }
    return MemoryReport(
        heapUsedBytes = heap.used,
        heapMaxBytes = heap.max,
        connections = connections,
    )
}

private suspend fun collectConnectionReport(
    index: Int,
    gameState: GameState,
): ConnectionMemoryReport {
    val viewModel = (gameState.screen as? GameScreen.ConnectedGameState)?.viewModel
    // Bounded like the usage read below: the report is a diagnostic, so it should always arrive,
    // even if some part of a wedged connection never answers.
    val name = withTimeoutOrNull(1.seconds) { gameState.getTitle().first() } ?: "unknown"
    val title = "Window ${index + 1}: $name"
    if (viewModel == null) {
        return ConnectionMemoryReport(title = title, usage = WindowMemoryUsage.EMPTY, runningScripts = 0)
    }
    // A stream reports from the work queue that owns its buffers, so a wedged or saturated queue
    // would otherwise hang the dialog. Report what we can instead.
    val usage = withTimeoutOrNull(5.seconds) { viewModel.memoryUsage() }
    return ConnectionMemoryReport(
        title = if (usage == null) "$title (did not respond)" else title,
        usage = usage ?: WindowMemoryUsage.EMPTY,
        runningScripts = viewModel.runningScriptCount,
    )
}

/** Writes a live-objects heap dump to [target], returning its size. */
private fun dumpHeap(target: File): Long {
    // dumpHeap refuses to overwrite, and the save dialog has already taken the user's confirmation
    // for this path, so clear it first.
    if (target.exists()) {
        target.delete()
    }
    val bean = ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean::class.java)
    // live = true runs a full GC first and dumps only reachable objects: a much smaller file, and
    // what you want when the question is what is being retained.
    bean.dumpHeap(target.absolutePath, true)
    return target.length()
}

private fun formatBytes(bytes: Long): String =
    when {
        bytes < 0 -> "unknown"
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
        bytes < 1024L * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024))
        else -> String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024))
    }

private fun formatCount(count: Long): String = String.format("%,d", count)
