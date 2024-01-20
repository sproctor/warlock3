package warlockfe.warlock3.compose.ui.settings

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import warlockfe.warlock3.core.text.StyleDefinition

@Composable
actual fun WindowSettingsDialog(
    onCloseRequest: () -> Unit,
    style: StyleDefinition,
    saveStyle: (StyleDefinition) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCloseRequest,
        confirmButton = {
            TextButton(onClick = onCloseRequest) {
                Text("OK")
            }
        },
        text = {
            Text("Not yet available on Android")
        }
    )
}