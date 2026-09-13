package warlockfe.warlock3.compose.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import warlockfe.warlock3.core.sge.StoredConnection

/**
 * Creates a telnet connection, or edits [existing]. The character name cannot change on an
 * existing connection: it is half of the id that all of the character's settings are saved under.
 */
@Composable
fun TelnetConnectionDialog(
    existing: StoredConnection?,
    // [connectNow] is true when the user chose to connect as well as save; only offered for a new
    // connection.
    onSave: (form: TelnetConnectionForm, connectNow: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val address = existing?.telnetAddress
    val nameState = rememberTextFieldState(existing?.name ?: "")
    val hostState = rememberTextFieldState(address?.host ?: "")
    val portState = rememberTextFieldState(address?.port?.toString() ?: "23")
    val characterState = rememberTextFieldState(existing?.character ?: "")
    val windowTitleState = rememberTextFieldState(existing?.windowTitle ?: "")
    var tls by remember { mutableStateOf(address?.tls ?: false) }
    var error: String? by remember { mutableStateOf(null) }

    fun submit(connectNow: Boolean) {
        validateTelnetForm(
            name = nameState.text.toString(),
            host = hostState.text.toString(),
            port = portState.text.toString(),
            tls = tls,
            character = characterState.text.toString(),
            windowTitle = windowTitleState.text.toString(),
        ).onSuccess { onSave(it, connectNow) }
            .onFailure { error = it.message }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "New telnet connection" else "Telnet connection") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (existing == null) {
                    Text(
                        "Connect to any MUD by its address. The MUD asks for your name and password itself, in the game window.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                TextField(
                    state = hostState,
                    label = { Text("Host") },
                    placeholder = { Text("mud.example.org") },
                    lineLimits = TextFieldLineLimits.SingleLine,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                TextField(
                    state = portState,
                    label = { Text("Port") },
                    lineLimits = TextFieldLineLimits.SingleLine,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = tls, onCheckedChange = { tls = it })
                    Text("Secure connection (TLS)")
                }
                TextField(
                    state = characterState,
                    enabled = existing == null,
                    label = { Text("Character") },
                    supportingText = { Text("Highlights, macros and scripts are saved per character.") },
                    lineLimits = TextFieldLineLimits.SingleLine,
                )
                TextField(
                    state = nameState,
                    label = { Text("Name") },
                    placeholder = { Text("Character @ host") },
                    lineLimits = TextFieldLineLimits.SingleLine,
                )
                TextField(
                    state = windowTitleState,
                    label = { Text("Window title") },
                    placeholder = { Text("Leave blank to use the character name") },
                    lineLimits = TextFieldLineLimits.SingleLine,
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (existing == null) {
                    TextButton(onClick = { submit(connectNow = false) }) { Text("Save") }
                    TextButton(onClick = { submit(connectNow = true) }) { Text("Save and connect") }
                } else {
                    TextButton(onClick = { submit(connectNow = false) }) { Text("OK") }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
