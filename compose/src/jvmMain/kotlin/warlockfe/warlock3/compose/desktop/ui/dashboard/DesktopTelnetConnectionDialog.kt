package warlockfe.warlock3.compose.desktop.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.Text
import warlockfe.warlock3.compose.desktop.shim.WarlockButton
import warlockfe.warlock3.compose.desktop.shim.WarlockDialog
import warlockfe.warlock3.compose.desktop.shim.WarlockOutlinedButton
import warlockfe.warlock3.compose.desktop.shim.WarlockTextField
import warlockfe.warlock3.compose.ui.dashboard.TelnetConnectionForm
import warlockfe.warlock3.compose.ui.dashboard.validateTelnetForm
import warlockfe.warlock3.core.sge.StoredConnection

/**
 * Creates a telnet connection, or edits [existing]. The character name cannot change on an
 * existing connection: it is half of the id that all of the character's settings are saved under.
 */
@Suppress("ktlint:compose:modifier-missing-check")
@Composable
fun DesktopTelnetConnectionDialog(
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
    var acceptScripts by remember { mutableStateOf(existing?.acceptScripts ?: true) }
    var error: String? by remember { mutableStateOf(null) }

    WarlockDialog(
        title = if (existing == null) "New telnet connection" else "Telnet connection settings",
        onCloseRequest = onDismiss,
        width = 520.dp,
        height = 560.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (existing == null) {
                Text("Connect to any MUD by its address. The MUD asks for your name and password itself, in the game window.")
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.weight(3f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Host")
                    WarlockTextField(
                        state = hostState,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = "mud.example.org",
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Port")
                    WarlockTextField(state = portState, modifier = Modifier.fillMaxWidth())
                }
            }

            CheckboxRow(
                checked = tls,
                onCheckedChange = { tls = it },
                text = "Secure connection (TLS)",
            )

            Text("Character")
            WarlockTextField(
                state = characterState,
                modifier = Modifier.fillMaxWidth(),
                enabled = existing == null,
                placeholder = "Highlights, macros and scripts are saved per character",
            )

            Text("Name")
            WarlockTextField(
                state = nameState,
                modifier = Modifier.fillMaxWidth(),
                placeholder = "Leave blank to name it after the character and host",
            )

            Text("Window title")
            WarlockTextField(
                state = windowTitleState,
                modifier = Modifier.fillMaxWidth(),
                placeholder = "Leave blank to use the character name",
            )

            CheckboxRow(
                checked = acceptScripts,
                onCheckedChange = { acceptScripts = it },
                text = "Run the script the MUD sends, if it sends one (Lua, over GMCP)",
            )

            error?.let { Text(it, color = JewelTheme.globalColors.text.error) }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                fun submit(connectNow: Boolean) {
                    validateTelnetForm(
                        name = nameState.text.toString(),
                        host = hostState.text.toString(),
                        port = portState.text.toString(),
                        tls = tls,
                        character = characterState.text.toString(),
                        windowTitle = windowTitleState.text.toString(),
                        acceptScripts = acceptScripts,
                    ).onSuccess { onSave(it, connectNow) }
                        .onFailure { error = it.message }
                }
                WarlockOutlinedButton(onClick = onDismiss, text = "Cancel")
                if (existing == null) {
                    WarlockOutlinedButton(onClick = { submit(connectNow = false) }, text = "Save")
                    WarlockButton(onClick = { submit(connectNow = true) }, text = "Save and connect")
                } else {
                    WarlockButton(onClick = { submit(connectNow = false) }, text = "OK")
                }
            }
        }
    }
}
