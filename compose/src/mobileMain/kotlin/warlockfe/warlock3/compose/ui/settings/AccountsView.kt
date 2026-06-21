package warlockfe.warlock3.compose.ui.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import warlockfe.warlock3.compose.components.ScrollableColumn
import warlockfe.warlock3.compose.generated.resources.Res
import warlockfe.warlock3.compose.generated.resources.delete
import warlockfe.warlock3.compose.generated.resources.edit
import warlockfe.warlock3.core.prefs.models.AccountEntity
import warlockfe.warlock3.core.prefs.repositories.AccountRepository

@Composable
fun AccountsView(
    accountRepository: AccountRepository,
    modifier: Modifier = Modifier,
) {
    val accounts by accountRepository.observeAll().collectAsState(emptyList())
    var editingPasswordFor by remember { mutableStateOf<AccountEntity?>(null) }
    var deleting by remember { mutableStateOf<AccountEntity?>(null) }
    val coroutineScope = rememberCoroutineScope()

    ScrollableColumn(modifier.fillMaxSize()) {
        accounts.forEach { account ->
            ListItem(
                headlineContent = { Text(account.username) },
                supportingContent = {
                    Text(if (account.password.isNullOrEmpty()) "No password saved" else "Password saved")
                },
                trailingContent = {
                    Row {
                        IconButton(onClick = { editingPasswordFor = account }) {
                            Icon(
                                painter = painterResource(Res.drawable.edit),
                                contentDescription = "Set password",
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        IconButton(onClick = { deleting = account }) {
                            Icon(
                                painter = painterResource(Res.drawable.delete),
                                contentDescription = "Delete",
                            )
                        }
                    }
                },
            )
        }
    }

    editingPasswordFor?.let { account ->
        SetPasswordDialog(
            username = account.username,
            savePassword = { password ->
                coroutineScope.launch {
                    accountRepository.save(account.copy(password = password))
                    editingPasswordFor = null
                }
            },
            onClose = { editingPasswordFor = null },
        )
    }

    deleting?.let { account ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete account") },
            text = { Text("Delete the saved account \"${account.username}\" and its password? Your characters are not affected.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            accountRepository.deleteByUsername(account.username)
                            deleting = null
                        }
                    },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun SetPasswordDialog(
    username: String,
    savePassword: (String) -> Unit,
    onClose: () -> Unit,
) {
    val password = rememberTextFieldState()

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Set password") },
        text = {
            TextField(
                state = password,
                label = { Text("Password for $username") },
                lineLimits = TextFieldLineLimits.SingleLine,
            )
        },
        confirmButton = {
            TextButton(onClick = { savePassword(password.text.toString()) }) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onClose) {
                Text("Cancel")
            }
        },
    )
}
