package warlockfe.warlock3.core.prefs.repositories

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.readString
import kotlinx.io.writeString
import warlockfe.warlock3.core.prefs.config.CharacterConfigStore

/**
 * Persists each character's command history as a plain-text file (`history.txt`, one command per
 * line, oldest first) alongside that character's config files. The history is loaded when the
 * character connects and rewritten whenever a new command is added.
 */
class CommandHistoryRepository(
    private val configStore: CharacterConfigStore,
    private val fileSystem: FileSystem,
    private val ioDispatcher: CoroutineDispatcher,
) {
    private fun fileFor(characterId: String): Path = Path(configStore.directoryFor(characterId), HISTORY_FILE_NAME)

    /** Reads the saved commands for [characterId], oldest first. Empty if there's no file yet. */
    suspend fun load(characterId: String): List<String> =
        withContext(ioDispatcher) {
            val path = fileFor(characterId)
            if (fileSystem.metadataOrNull(path) == null) return@withContext emptyList()
            runCatching {
                fileSystem
                    .source(path)
                    .buffered()
                    .use { it.readString() }
                    .lineSequence()
                    .filter { it.isNotEmpty() }
                    .toList()
            }.onFailure {
                Logger.e(it) { "Failed to read command history $path" }
            }.getOrDefault(emptyList())
        }

    /** Overwrites the history file for [characterId] with [commands] (oldest first). */
    suspend fun save(
        characterId: String,
        commands: List<String>,
    ) = withContext(ioDispatcher + NonCancellable) {
        val path = fileFor(characterId)
        val dir = path.parent
        runCatching {
            if (commands.isEmpty()) {
                if (fileSystem.metadataOrNull(path) != null) fileSystem.delete(path)
                return@runCatching
            }
            if (dir != null && fileSystem.metadataOrNull(dir) == null) fileSystem.createDirectories(dir)
            val text = commands.joinToString(separator = "\n", postfix = "\n")
            // Write to a temp file and atomically move it into place so a crash mid-write can't leave a
            // truncated history file.
            val tmp = Path(dir ?: path, "$HISTORY_FILE_NAME.tmp")
            fileSystem.sink(tmp).buffered().use { it.writeString(text) }
            fileSystem.atomicMove(tmp, path)
        }.onFailure {
            Logger.e(it) { "Failed to write command history $path" }
        }
    }

    companion object {
        const val HISTORY_FILE_NAME = "history.txt"
    }
}
