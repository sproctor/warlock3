package warlockfe.warlock3.core.prefs.config

import dev.eav.tomlkt.Toml
import dev.eav.tomlkt.TomlElement
import dev.eav.tomlkt.encodeToString
import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.writeString
import kotlinx.serialization.KSerializer

// Low-level file persistence shared by the config stores (CharacterConfigStore and TomlFileStore),
// which both write TOML via a temp file + atomic move and carry a parsed template forward so
// hand-written comments survive a rewrite.

/** Atomically replace [path] with [text] via a sibling temp file and [FileSystem.atomicMove]. */
internal fun FileSystem.writeTextAtomically(
    path: Path,
    text: String,
) {
    val tmp = Path(path.parent ?: path, path.name + ".tmp")
    sink(tmp).buffered().use { it.writeString(text) }
    atomicMove(tmp, path)
}

/** Create [path]'s parent directory if it does not already exist. */
internal fun FileSystem.ensureParentDir(path: Path) {
    val parent = path.parent
    if (parent != null && metadataOrNull(parent) == null) {
        createDirectories(parent)
    }
}

/**
 * Encode [value] to TOML, carrying the comments/formatting of [template] (the last-parsed document)
 * when present so hand-written comments survive the rewrite. [elementKey] matches array-of-table
 * entries across reorders (by `id`) so a comment follows its entry.
 */
internal fun <T> Toml.encodeWithTemplate(
    serializer: KSerializer<T>,
    value: T,
    template: TomlElement?,
    elementKey: (TomlElement) -> Any? = CONFIG_ELEMENT_KEY,
): String =
    if (template != null) {
        encodeToString(serializer, value, template, elementKey)
    } else {
        encodeToString(serializer, value)
    }
