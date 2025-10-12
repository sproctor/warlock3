package warlockfe.warlock3.compose.ui.sge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.SecureTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import warlockfe.warlock3.core.prefs.models.AccountEntity

@Composable
fun AccountsView(
    initialUsername: String?,
    initialPassword: String?,
    onAccountSelect: (AccountEntity) -> Unit,
    onCancel: () -> Unit,
) {
    val username = rememberTextFieldState(initialUsername ?: "")
    val password = rememberTextFieldState(initialPassword ?: "")

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
                    state = username,
                    label = { Text("Username") },
                    lineLimits = TextFieldLineLimits.SingleLine,
                )
                // TODO: add a way to disable the masking here
                SecureTextField(
                    modifier = Modifier.padding(8.dp),
                    state = password,
                    label = { Text("Password") },
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
                onClick = {
                    onAccountSelect(AccountEntity(username.text.toString().trim(), password.text.toString().trim()))
                }
            ) {
                Text("Next")
            }
        }
    }
}
