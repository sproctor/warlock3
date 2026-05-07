package warlockfe.warlock3.compose.desktop.ui.settings

import androidx.compose.runtime.Composable
import org.jetbrains.jewel.ui.component.Text
import warlockfe.warlock3.compose.desktop.shim.WarlockAlertDialog
import warlockfe.warlock3.compose.desktop.shim.WarlockButton
import warlockfe.warlock3.compose.desktop.shim.WarlockOutlinedButton

@Suppress("ktlint:compose:modifier-missing-check")
@Composable
fun DesktopConfirmationDialog(
    title: String,
    text: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    WarlockAlertDialog(
        title = title,
        text = text,
        onDismissRequest = onDismiss,
        confirmButton = {
            WarlockButton(onClick = onConfirm) { Text("Confirm") }
        },
        dismissButton = {
            WarlockOutlinedButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
