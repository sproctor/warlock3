package warlockfe.warlock3.compose.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun ConnectionSettingsDialog(
    name: String,
    windowTitle: String?,
    updateName: (String) -> Unit,
    updateWindowTitle: (String?) -> Unit,
    closeDialog: () -> Unit,
) {
    val nameState = rememberTextFieldState(name)
    val windowTitleState = rememberTextFieldState(windowTitle ?: "")
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = closeDialog,
        confirmButton = {
            TextButton(
                onClick = {
                    scope.launch {
                        val newName = nameState.text.toString().trim()
                        if (newName.isNotEmpty() && newName != name) {
                            updateName(newName)
                        }
                        val trimmedTitle = windowTitleState.text.toString().trim()
                        val newWindowTitle = trimmedTitle.ifBlank { null }
                        if (newWindowTitle != windowTitle) {
                            updateWindowTitle(newWindowTitle)
                        }
                        closeDialog()
                    }
                },
            ) {
                Text("OK")
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                TextField(
                    state = nameState,
                    label = {
                        Text("Name")
                    },
                    lineLimits = TextFieldLineLimits.SingleLine,
                )
                TextField(
                    state = windowTitleState,
                    label = {
                        Text("Window title")
                    },
                    placeholder = {
                        Text("Leave blank to use the character name")
                    },
                    lineLimits = TextFieldLineLimits.SingleLine,
                )
            }
        },
    )
}
