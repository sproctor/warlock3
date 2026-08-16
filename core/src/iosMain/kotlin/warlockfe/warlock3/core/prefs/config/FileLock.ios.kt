package warlockfe.warlock3.core.prefs.config

import kotlinx.io.files.Path

// iOS apps are single-process, so there's no cross-process contention to guard against; the store's
// in-memory mutex already serializes writes.
internal actual fun withFileLock(
    lockFile: Path,
    block: () -> Unit,
) = block()

internal actual fun tryWithFileLock(
    lockFile: Path,
    block: () -> Unit,
): Boolean {
    block()
    return true
}
