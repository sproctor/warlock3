package warlockfe.warlock3.compose.ui.window

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import warlockfe.warlock3.core.prefs.models.WindowSettings
import warlockfe.warlock3.core.text.FontConfig
import warlockfe.warlock3.core.text.StyleDefinition
import warlockfe.warlock3.core.window.WindowInfo

@Stable
data class WindowUiState(
    val name: String,
    val windowInfo: MutableState<WindowInfo?>,
    val style: StyleDefinition,
    // Per-window font overrides; null falls back to the character default / monospace font.
    val font: FontConfig? = null,
    val monoFont: FontConfig? = null,
    val width: Int?,
    val height: Int?,
    val data: WindowData?,
    val nameFilter: Boolean = false,
)

sealed interface WindowData

data class StreamWindowData(
    val stream: ComposeTextStream,
) : WindowData

data class DialogWindowData(
    val dialogData: ComposeDialogState,
) : WindowData

/** The window's color + weight/italic/underline styling. Fonts are carried separately on [WindowUiState]. */
fun WindowSettings.getStyle(): StyleDefinition =
    StyleDefinition(
        textColor = textColor,
        backgroundColor = backgroundColor,
        bold = bold,
        italic = italic,
        underline = underline,
    )
