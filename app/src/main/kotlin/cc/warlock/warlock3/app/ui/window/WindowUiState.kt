package cc.warlock.warlock3.app.ui.window

import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import cc.warlock.warlock3.app.model.ViewHighlight
import cc.warlock.warlock3.core.text.StyleDefinition
import cc.warlock.warlock3.core.window.Window
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.Flow

@Stable
class WindowUiState(
    val name: String,
    val stream: ComposeTextStream,
    val window: Window?,
    val defaultStyle: StyleDefinition,
    val highlights: List<ViewHighlight>,
    val presets: Map<String, StyleDefinition>,
    val allowSelection: Boolean,
)