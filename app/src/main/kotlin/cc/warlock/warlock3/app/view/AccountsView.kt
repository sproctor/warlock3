package cc.warlock.warlock3.app.view

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import cc.warlock.warlock3.app.model.Account

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AccountsView(
    accountSelected: (Account) -> Unit
) {
    // TODO: Add saved account select list here
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

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
                        .onKeyEvent {
                            if (it.key.keyCode == Key.Tab.keyCode) {
                                focusManager.moveFocus(FocusDirection.Next)
                                true
                            } else {
                                false
                            }
                        },
                    value = username,
                    onValueChange = {
                        if (!it.contains('\t')) { // work around bug in compose 1.0.0-alpha3 that passes a tab through although it should be interecepted
                            username = it
                        }
                    },
                    label = { Text("Username") },
                )
                TextField(
                    modifier = Modifier.padding(8.dp)
                        .onKeyEvent {
                            if (it.key.keyCode == Key.Tab.keyCode) {
                                focusManager.moveFocus(FocusDirection.Next)
                                true
                            } else {
                                false
                            }
                        },
                    value = password,
                    onValueChange = {
                        if (!it.contains('\t')) { // work around bug in compose 1.0.0-alpha3 that passes a tab through although it should be interecepted
                            password = it
                        }
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
                onClick = { accountSelected(Account(username, password)) }
            ) {
                Text("Next")
            }
        }
    }
}
