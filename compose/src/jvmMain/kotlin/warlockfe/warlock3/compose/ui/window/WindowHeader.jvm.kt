package warlockfe.warlock3.compose.ui.window

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
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
    onMoveClicked: (WindowLocation) -> Unit,
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
                add(
                    ContextMenuItem(
                        label = "Hide window",
                        onClick = onCloseClicked,
                    )
                )
                if (location != WindowLocation.MAIN) {
                    WindowLocation.entries.forEach { otherLocation ->
                        if (location != otherLocation && otherLocation != WindowLocation.MAIN) {
                            add(
                                ContextMenuItem(
                                    label = "Move to ${otherLocation.value.lowercase()} slot",
                                    onClick = { onMoveClicked(otherLocation) }
                                )
                            )
                        }
                    }
                }
            }
        }
    ) {
        Box(modifier = modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            title()
        }
    }
}