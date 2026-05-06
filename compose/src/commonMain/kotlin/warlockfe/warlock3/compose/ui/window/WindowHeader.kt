package warlockfe.warlock3.compose.ui.window

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import warlockfe.warlock3.core.window.WindowLocation

@Composable
expect fun WindowHeader(
    title: @Composable () -> Unit,
    location: WindowLocation,
    isSelected: Boolean,
    onSettingsClick: () -> Unit,
    onClearClick: () -> Unit,
    onCloseClick: () -> Unit,
    modifier: Modifier = Modifier,
)
