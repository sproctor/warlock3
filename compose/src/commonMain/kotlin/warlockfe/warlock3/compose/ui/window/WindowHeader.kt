package warlockfe.warlock3.compose.ui.window

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import warlockfe.warlock3.core.window.WindowLocation

@Composable
expect fun WindowHeader(
    modifier: Modifier,
    title: @Composable () -> Unit,
    location: WindowLocation,
    isSelected: Boolean,
    onSettingsClicked: () -> Unit,
    onClearClicked: () -> Unit,
    onCloseClicked: () -> Unit,
    onMoveClicked: (WindowLocation) -> Unit,
)