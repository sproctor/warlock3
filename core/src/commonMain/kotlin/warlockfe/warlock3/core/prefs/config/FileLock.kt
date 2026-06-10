package warlockfe.warlock3.core.prefs.config

import kotlinx.io.files.Path

/**
 * Runs [block] while holding an exclusive, cross-process advisory lock keyed on [lockFile] (a
 * sibling `.lock` file of the config being written), so two app instances can't interleave writes to
 * the same config file. On single-process platforms (iOS) it simply runs [block].
 *
 * The lock is best-effort: if it can't be acquired (unsupported filesystem, IO error) the block
 * still runs, degrading to no cross-process coordination rather than dropping the user's write.
 */
internal expect fun withFileLock(
    lockFile: Path,
    block: () -> Unit,
)
