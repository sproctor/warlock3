package warlockfe.warlock3.compose.ui.window

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import warlockfe.warlock3.core.window.WindowLocation

@Composable
actual fun WindowHeader(
    modifier: Modifier,
    title: @Composable () -> Unit,
    location: WindowLocation,
    isSelected: Boolean,
    onSettingsClicked: () -> Unit,
    onClearClicked: () -> Unit,
    onCloseClicked: () -> Unit,
) {
    ContextMenuArea(
        items = {
            buildList {
                add(
                    ContextMenuItem(
                        label = "Window Settings ...",
                        onClick = onSettingsClicked,
                    )
                )
                add(
                    ContextMenuItem(
                        label = "Clear window",
                        onClick = onClearClicked,
                    )
                )
                if (location != WindowLocation.MAIN) {
                    add(
                        ContextMenuItem(
                            label = "Hide window",
                            onClick = onCloseClicked,
                        )
                    )
                }
            }
        }
    ) {
        Box(modifier = modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            title()
        }
    }
}
