package cc.warlock.warlock3.app.ui.window

import cc.warlock.warlock3.app.model.ViewHighlight
import cc.warlock.warlock3.core.text.StyleDefinition
import cc.warlock.warlock3.core.text.StyledString
import cc.warlock.warlock3.core.window.Window
import cc.warlock.warlock3.stormfront.stream.StreamLine
import kotlinx.coroutines.flow.StateFlow

class WindowUiState(
    val name: String,
    val lines: StateFlow<List<StreamLine>>,
    val window: Window?,
    val components: Map<String, StyledString>,
    val highlights: List<ViewHighlight>,
    val presets: Map<String, StyleDefinition>
)