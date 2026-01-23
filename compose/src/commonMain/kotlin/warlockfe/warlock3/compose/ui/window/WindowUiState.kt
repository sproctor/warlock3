package warlockfe.warlock3.compose.ui.window

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import warlockfe.warlock3.core.prefs.models.WindowSettingsEntity
import warlockfe.warlock3.core.text.StyleDefinition
import warlockfe.warlock3.core.window.WindowInfo
import warlockfe.warlock3.core.window.WindowLocation

@Stable
data class WindowUiState(
    val name: String,
    val windowInfo: MutableState<WindowInfo?>,
    val style: StyleDefinition,
    val width: Int?,
    val height: Int?,
    val data: WindowData?,
)

sealed interface WindowData

data class StreamWindowData(
    val stream: ComposeTextStream,
) : WindowData

data class DialogWindowData(
    val dialogData: ComposeDialogState,
) : WindowData

fun WindowSettingsEntity.getStyle(): StyleDefinition {
    return StyleDefinition(
        textColor = textColor,
        backgroundColor = backgroundColor,
        fontFamily = fontFamily,
        fontSize = fontSize,
    )
}
