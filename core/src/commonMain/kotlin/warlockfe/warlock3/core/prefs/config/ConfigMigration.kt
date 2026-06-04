package warlockfe.warlock3.core.prefs.config

import co.touchlab.kermit.Logger
import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.writeString
import warlockfe.warlock3.core.prefs.dao.CharacterDao
import warlockfe.warlock3.core.prefs.dao.HighlightDao
import warlockfe.warlock3.core.prefs.dao.NameDao
import warlockfe.warlock3.core.prefs.dao.VariableDao
import warlockfe.warlock3.core.prefs.mappers.toHighlight

/**
 * One-time migration of highlights, names, and variables out of the SQLite database and into the
 * human-editable TOML config files. Runs once (guarded by a marker file), reading the existing rows
 * through the legacy DAOs and writing them through the [CharacterConfigStore]. The database tables
 * are left intact so this is non-destructive and reversible.
 */
class ConfigMigration(
    private val store: CharacterConfigStore,
    private val characterDao: CharacterDao,
    private val highlightDao: HighlightDao,
    private val nameDao: NameDao,
    private val variableDao: VariableDao,
    private val fileSystem: FileSystem,
    configDirectory: String,
) {
    private val rootDir = Path(configDirectory)
    private val markerFile = Path(rootDir, ".config-migrated")

    suspend fun migrateIfNeeded() {
        if (fileSystem.metadataOrNull(markerFile) != null) return

        val characterIds =
            buildList {
                add(GLOBAL_CHARACTER_ID)
                runCatching { characterDao.getAll() }
                    .getOrDefault(emptyList())
                    .forEach { add(it.id) }
            }.distinct()

        runCatching {
            for (id in characterIds) {
                val highlights = highlightDao.getHighlightsByCharacter(id).map { it.toHighlight().toConfig() }
                val names = nameDao.getByCharacter(id).map { it.toConfig() }
                val variables = variableDao.getAllByCharacter(id).associate { it.name to it.value }
                if (highlights.isEmpty() && names.isEmpty() && variables.isEmpty()) continue
                store.mutate(id) { current ->
                    current.copy(
                        highlights = highlights,
                        names = names,
                        variables = variables,
                    )
                }
            }
            writeMarker()
        }.onFailure {
            Logger.e(it) { "Failed to migrate highlights/names/variables to config files" }
        }
    }

    private fun writeMarker() {
        if (fileSystem.metadataOrNull(rootDir) == null) {
            fileSystem.createDirectories(rootDir)
        }
        fileSystem.sink(markerFile).buffered().use {
            it.writeString("Migrated highlights, names, and variables to TOML config files.\n")
        }
    }
}
