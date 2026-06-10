package warlockfe.warlock3.core.prefs.config

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

// iOS is single-process and these configs aren't edited by an external editor, so there are no
// out-of-band changes to watch for.
internal actual fun watchConfigChanges(rootDir: String): Flow<String> = emptyFlow()
