package warlockfe.warlock3.compose.ui.sge

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import warlockfe.warlock3.core.prefs.models.AccountEntity

@Composable
fun AccountsView(
    initialUsername: String?,
    initialPassword: String?,
    onAccountSelect: (AccountEntity) -> Unit,
    onCancel: () -> Unit,
) {
    var username by remember(initialUsername) { mutableStateOf(initialUsername ?: "") }
    var password by remember(initialPassword) { mutableStateOf(initialPassword ?: "") }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.align(Alignment.Center)
            ) {
                TextField(
                    modifier = Modifier.padding(8.dp),
                    value = username,
                    onValueChange = {
                        username = it
                    },
                    label = { Text("Username") },
                    singleLine = true,
                )
                TextField(
                    modifier = Modifier.padding(8.dp),
                    value = password,
                    onValueChange = {
                        password = it
                    },
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation('*'),
                    singleLine = true,
                )
            }
        }
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Button(
                modifier = Modifier.padding(end = 16.dp),
                onClick = onCancel
            ) {
                Text("Cancel")
            }
            Button(
                onClick = { onAccountSelect(AccountEntity(username.trim(), password.trim())) }
            ) {
                Text("Next")
            }
        }
    }
}
