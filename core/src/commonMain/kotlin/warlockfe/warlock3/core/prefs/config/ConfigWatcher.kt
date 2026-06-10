package warlockfe.warlock3.core.prefs.config

import kotlinx.coroutines.flow.Flow

/**
 * Emits the absolute path of any `.toml` file under [rootDir] that is created or modified on disk,
 * so the store can reload configs that were changed out of band: a hand edit in a text editor, or a
 * save from another running app instance. The flow runs until cancelled.
 *
 * No-op (empty) on platforms with no external editors and a single process (iOS).
 */
internal expect fun watchConfigChanges(rootDir: String): Flow<String>
