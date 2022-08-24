package cc.warlock.warlock3.app.ui.window

import androidx.compose.runtime.Immutable
import cc.warlock.warlock3.core.text.StyledString
import cc.warlock.warlock3.core.window.StreamLine
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentMap
import java.util.*

@Immutable
data class StreamSnapshot(
    val id: UUID,
    val lines: ImmutableList<StreamLine>,
    val components: PersistentMap<String, StyledString>,
)
