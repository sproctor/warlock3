package warlockfe.warlock3.app.ui.window

import androidx.compose.runtime.Stable
import warlockfe.warlock3.app.model.ViewHighlight
import warlockfe.warlock3.core.text.StyleDefinition
import warlockfe.warlock3.core.window.Window

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