package warlockfe.warlock3.compose.desktop.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.input.rememberTextFieldState
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
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import warlockfe.warlock3.compose.desktop.shim.WarlockButton
import warlockfe.warlock3.compose.desktop.shim.WarlockDialog
import warlockfe.warlock3.compose.desktop.shim.WarlockListItem
import warlockfe.warlock3.compose.desktop.shim.WarlockOutlinedButton
import warlockfe.warlock3.compose.desktop.shim.WarlockPasswordField
import warlockfe.warlock3.compose.desktop.shim.WarlockScrollableColumn
import warlockfe.warlock3.core.prefs.models.AccountEntity
import warlockfe.warlock3.core.prefs.repositories.AccountRepository

@Composable
fun DesktopAccountsView(
    accountRepository: AccountRepository,
    modifier: Modifier = Modifier,
) {
    val accounts by accountRepository.observeAll().collectAsState(emptyList())
    var editingPasswordFor by remember { mutableStateOf<AccountEntity?>(null) }
    var deleting by remember { mutableStateOf<AccountEntity?>(null) }
    val scope = rememberCoroutineScope()

    Column(modifier.fillMaxSize()) {
        Text("Accounts")
        Spacer(Modifier.height(8.dp))
        if (accounts.isEmpty()) {
            Text(
                text = "No saved accounts. Accounts are saved automatically when you log in.",
                color = JewelTheme.globalColors.text.info,
            )
        }
        WarlockScrollableColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
            accounts.forEach { account ->
                WarlockListItem(
                    headline = {
                        Column {
                            Text(account.username)
                            Text(
                                text = if (account.password.isNullOrEmpty()) "No password saved" else "Password saved",
                                color = JewelTheme.globalColors.text.info,
                            )
                        }
                    },
                    trailing = {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            WarlockOutlinedButton(
                                onClick = { editingPasswordFor = account },
                                text = "Set password",
                            )
                            WarlockOutlinedButton(
                                onClick = { deleting = account },
                                text = "Delete",
                            )
                        }
                    },
                )
            }
        }
    }

    editingPasswordFor?.let { account ->
        DesktopSetPasswordDialog(
            username = account.username,
            savePassword = { password ->
                scope.launch {
                    accountRepository.save(account.copy(password = password))
                    editingPasswordFor = null
                }
            },
            onClose = { editingPasswordFor = null },
        )
    }

    deleting?.let { account ->
        DesktopConfirmationDialog(
            title = "Delete account",
            text = "Delete the saved account \"${account.username}\" and its password? Your characters are not affected.",
            onDismiss = { deleting = null },
            onConfirm = {
                scope.launch {
                    accountRepository.deleteByUsername(account.username)
                    deleting = null
                }
            },
        )
    }
}

@Composable
private fun DesktopSetPasswordDialog(
    username: String,
    savePassword: (String) -> Unit,
    onClose: () -> Unit,
) {
    val password = rememberTextFieldState()

    WarlockDialog(
        title = "Set password",
        onCloseRequest = onClose,
        width = 480.dp,
        height = 220.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Password for $username")
            WarlockPasswordField(state = password, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.End),
            ) {
                WarlockOutlinedButton(onClick = onClose, text = "Cancel")
                WarlockButton(
                    onClick = { savePassword(password.text.toString()) },
                    text = "OK",
                )
            }
        }
    }
}
