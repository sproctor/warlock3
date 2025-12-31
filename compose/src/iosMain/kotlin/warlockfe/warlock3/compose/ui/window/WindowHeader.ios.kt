package warlockfe.warlock3.compose.ui.window

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.painterResource
import warlockfe.warlock3.compose.generated.resources.Res
import warlockfe.warlock3.compose.generated.resources.more_vert
import warlockfe.warlock3.core.window.WindowLocation

// TODO: make a nonJvm source set and merge this with Android
@Composable
actual fun WindowHeader(
    modifier: Modifier,
    title: @Composable (() -> Unit),
    location: WindowLocation,
    isSelected: Boolean,
    onSettingsClicked: () -> Unit,
    onClearClicked: () -> Unit,
    onCloseClicked: () -> Unit,
    onMoveClicked: (WindowLocation) -> Unit
) {
    Row(modifier = modifier) {
        Box(modifier = Modifier.weight(1f)) {
            title()
        }
        Box {
            var showMenu by remember { mutableStateOf(false) }
            IconButton(onClick = { showMenu = !showMenu }) {
                Icon(
                    painterResource(Res.drawable.more_vert),
                    contentDescription = null,
                )
            }
            WindowViewDropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                onSettingsClicked = onSettingsClicked,
                location = location,
                onMoveClicked = onMoveClicked,
                onClearClicked = onClearClicked,
                onCloseClicked = onCloseClicked,
            )
        }
    }
}

@Composable
private fun WindowViewDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onSettingsClicked: () -> Unit,
    location: WindowLocation,
    onMoveClicked: (WindowLocation) -> Unit,
    onClearClicked: () -> Unit,
    onCloseClicked: () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest
    ) {
        DropdownMenuItem(
            onClick = {
                onSettingsClicked()
                onDismissRequest()
            },
            text = { Text("Window Settings ...") }
        )
        DropdownMenuItem(
            onClick = {
                onClearClicked()
                onDismissRequest()
            },
            text = { Text("Clear window") },
        )
        DropdownMenuItem(
            onClick = {
                onCloseClicked()
                onDismissRequest()
            },
            text = { Text("Hide window") },
        )
        if (location != WindowLocation.MAIN) {
            WindowLocation.entries.forEach { otherLocation ->
                if (location != otherLocation && otherLocation != WindowLocation.MAIN) {
                    DropdownMenuItem(
                        text = { Text("Move to ${otherLocation.value.lowercase()} slot") },
                        onClick = {
                            onMoveClicked(otherLocation)
                            onDismissRequest()
                        }
                    )
                }
            }
        }
    }
}
