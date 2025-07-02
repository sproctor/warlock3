package warlockfe.warlock3.compose.ui.window

import androidx.compose.runtime.Stable
import warlockfe.warlock3.compose.model.ViewHighlight
import warlockfe.warlock3.core.client.DialogObject
import warlockfe.warlock3.core.text.StyleDefinition
import warlockfe.warlock3.core.window.Window

@Stable
sealed interface WindowUiState {
    val name: String
    val window: Window?
}

@Stable
data class StreamWindowUiState(
    override val name: String,
    val stream: ComposeTextStream,
    override val window: Window?,
    val defaultStyle: StyleDefinition,
    val highlights: List<ViewHighlight>,
    val presets: Map<String, StyleDefinition>,
) : WindowUiState

@Stable
data class DialogWindowUiState(
    override val name: String,
    override val window: Window?,
    val dialogData: List<DialogObject>,
    val style: StyleDefinition,
    val width: Int?,
    val height: Int?,
) : WindowUiState
