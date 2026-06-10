package warlockfe.warlock3.core.prefs.config

import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.file.ClosedWatchServiceException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds.ENTRY_CREATE
import java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY
import java.nio.file.StandardWatchEventKinds.OVERFLOW
import java.nio.file.WatchKey

internal actual fun watchConfigChanges(rootDir: String): Flow<String> =
    callbackFlow {
        val root = Path.of(rootDir)
        runCatching { Files.createDirectories(root) }
        val watchService = FileSystems.getDefault().newWatchService()
        val keys = HashMap<WatchKey, Path>()

        // WatchService is not recursive, so register the root and every existing subdirectory, and
        // pick up new subdirectories (e.g. characters/<gameCode>) as they appear.
        fun register(dir: Path) {
            runCatching {
                if (Files.isDirectory(dir)) {
                    keys[dir.register(watchService, ENTRY_CREATE, ENTRY_MODIFY)] = dir
                }
            }
        }

        fun registerTree(start: Path) {
            if (!Files.exists(start)) return
            runCatching {
                Files.walk(start).use { stream ->
                    stream.filter { Files.isDirectory(it) }.forEach { register(it) }
                }
            }
        }

        registerTree(root)

        val job =
            launch(Dispatchers.IO) {
                try {
                    while (isActive) {
                        val key = watchService.take()
                        val dir = keys[key]
                        if (dir != null) {
                            for (event in key.pollEvents()) {
                                if (event.kind() == OVERFLOW) continue
                                val name = event.context() as? Path ?: continue
                                val child = dir.resolve(name)
                                if (event.kind() == ENTRY_CREATE && Files.isDirectory(child)) {
                                    registerTree(child)
                                } else if (child.toString().endsWith(".toml")) {
                                    trySend(child.toString())
                                }
                            }
                        }
                        if (!key.reset()) {
                            keys.remove(key)
                            if (keys.isEmpty()) break
                        }
                    }
                } catch (_: ClosedWatchServiceException) {
                    // Expected when the flow is cancelled and we close the service below.
                } catch (e: Exception) {
                    Logger.w(e) { "Config file watch loop stopped" }
                }
            }

        awaitClose {
            job.cancel()
            runCatching { watchService.close() }
        }
    }
