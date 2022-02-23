package cc.warlock.warlock3.app.views.sge

import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import cc.warlock.warlock3.core.prefs.models.Account

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AccountsView(
    initialUsername: String?,
    initialPassword: String?,
    onAccountSelect: (Account) -> Unit
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
                val focusManager = LocalFocusManager.current
                TextField(
                    modifier = Modifier.padding(8.dp)
                        .onPreviewKeyEvent {
                            if (it.key.keyCode == Key.Tab.keyCode) {
                                if (it.type == KeyEventType.KeyDown) {
                                    focusManager.moveFocus(FocusDirection.Next)
                                }
                                true
                            } else {
                                false
                            }
                        },
                    value = username,
                    onValueChange = {
                        username = it
                    },
                    label = { Text("Username") },
                )
                TextField(
                    modifier = Modifier.padding(8.dp)
                        .onPreviewKeyEvent {
                            if (it.key.keyCode == Key.Tab.keyCode) {
                                if (it.type == KeyEventType.KeyDown) {
                                    focusManager.moveFocus(FocusDirection.Next)
                                }
                                true
                            } else {
                                false
                            }
                        },
                    value = password,
                    onValueChange = {
                        password = it
                    },
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation('*')
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
                onClick = { onAccountSelect(Account(username, password)) }
            ) {
                Text("Next")
            }
        }
    }
}
