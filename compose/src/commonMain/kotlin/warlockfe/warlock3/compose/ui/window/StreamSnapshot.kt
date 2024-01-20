package warlockfe.warlock3.compose.ui.window

import androidx.compose.runtime.Immutable
import warlockfe.warlock3.core.text.StyledString
import warlockfe.warlock3.core.window.StreamLine
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentMap
import java.util.*

@Immutable
data class StreamSnapshot(
    val id: UUID,
    val lines: ImmutableList<StreamLine>,
    val components: PersistentMap<String, StyledString>,
)
