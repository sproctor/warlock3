package warlockfe.warlock3.core.prefs.config

import dev.eav.tomlkt.Toml
import dev.eav.tomlkt.TomlElement
import dev.eav.tomlkt.encodeToString
import kotlinx.io.IOException
import kotlinx.io.buffered
import kotlinx.io.files.FileNotFoundException
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.readString
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

/**
 * The names [dir] directly contains, or an empty set when there is no such directory.
 *
 * Used instead of `metadataOrNull(Path(dir, name))` to decide whether a settings file is there,
 * because that call cannot tell "not there" from "cannot look": stat on a child of a directory we
 * are not allowed to search returns null exactly as it does for a file that never existed. Nor can
 * the open discriminate -- kotlinx-io reports both as `FileNotFoundException`, separated only by a
 * message ("No such file or directory" against "Permission denied") in one language. A listing
 * answers the question directly, and closes the stat-then-open race besides.
 *
 * Only a missing directory counts as empty. Every other listing failure is a directory we cannot
 * read rather than one that isn't there, and folding the two together would give back the bug this
 * helper exists to fix -- the caller would see no settings and go on to save over them. Under
 * [failOnUnreadable] those are rethrown for the startup read to turn fatal; the write path has no
 * launch left to abort, so there they keep the older behavior of carrying on.
 *
 * One case no caller can answer: a directory that cannot be entered at all lists as empty rather
 * than failing (verified against kotlinx-io on the JVM), so it is indistinguishable from an empty
 * one. That leaves such a directory looking like a fresh install -- but a directory that denies
 * reads denies writes too, so the settings under it cannot then be overwritten, and the save failure
 * is reported in its own right.
 */
internal fun FileSystem.entryNames(
    dir: Path,
    failOnUnreadable: Boolean = false,
): Set<String> =
    try {
        list(dir).mapTo(mutableSetOf()) { it.name }
    } catch (_: FileNotFoundException) {
        // No such directory, so nothing inside it: a fresh install, not damage.
        emptySet()
    } catch (e: IOException) {
        if (failOnUnreadable) throw e
        emptySet()
    }

/**
 * The text of [path], or null when the file genuinely is not there -- see [entryNames] for how that
 * is told apart from a file we are not allowed to read.
 *
 * Throws when the file is there but cannot be read, which is what lets a startup read stop the app
 * rather than mistake damaged settings for a fresh install.
 */
internal fun FileSystem.readTextOrNullIfAbsent(
    path: Path,
    failOnUnreadable: Boolean = false,
): String? {
    val parent = path.parent ?: return null
    if (path.name !in entryNames(parent, failOnUnreadable)) return null
    return source(path).buffered().use { it.readString() }
}
