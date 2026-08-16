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

/**
 * Like [withFileLock], but reports whether the lock was actually held, and runs [block] only when it
 * was. Where [withFileLock] would rather write unlocked than drop a write, a caller about to do
 * something destructive needs the opposite: it must be able to back off when it cannot prove it has
 * the file to itself. Returns false without running [block] if the lock could not be taken.
 *
 * On single-process platforms (iOS) the caller is trivially the only one that can touch the file, so
 * [block] runs and this returns true.
 */
internal expect fun tryWithFileLock(
    lockFile: Path,
    block: () -> Unit,
): Boolean
