package cc.warlock.warlock3.app.ui.window

import androidx.compose.runtime.Stable
import cc.warlock.warlock3.core.text.StyleDefinition
import cc.warlock.warlock3.core.window.Window
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.Flow

@Stable
class WindowUiState(
    val name: String,
    val lines: Flow<ImmutableList<WindowLine>>,
    val window: Window?,
    val defaultStyle: StyleDefinition,
)