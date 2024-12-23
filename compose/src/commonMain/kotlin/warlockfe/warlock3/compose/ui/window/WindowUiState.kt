package warlockfe.warlock3.compose.ui.window

import androidx.compose.runtime.Stable
import warlockfe.warlock3.compose.model.ViewHighlight
import warlockfe.warlock3.core.text.StyleDefinition
import warlockfe.warlock3.core.window.Window

@Stable
data class WindowUiState(
    val name: String,
    val stream: ComposeTextStream,
    val window: Window?,
    val defaultStyle: StyleDefinition,
    val highlights: List<ViewHighlight>,
    val presets: Map<String, StyleDefinition>,
)