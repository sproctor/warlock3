package warlockfe.warlock3.compose.ui.settings

import androidx.compose.runtime.Composable
import warlockfe.warlock3.core.text.StyleDefinition

@Composable
expect fun WindowSettingsDialog(
    onCloseRequest: () -> Unit,
    style: StyleDefinition,
    saveStyle: (StyleDefinition) -> Unit,
)
