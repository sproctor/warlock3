package cc.warlock.warlock3.app.ui.window

import androidx.compose.ui.text.AnnotatedString
import cc.warlock.warlock3.app.model.ViewHighlight
import cc.warlock.warlock3.core.text.StyleDefinition
import cc.warlock.warlock3.core.text.StyledString
import cc.warlock.warlock3.core.window.Window
import cc.warlock.warlock3.stormfront.stream.StreamLine
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

class WindowUiState(
    val name: String,
    val lines: Flow<ImmutableList<WindowLine>>,
    val window: Window?,
    val defaultStyle: StyleDefinition,
)