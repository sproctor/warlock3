package warlockfe.warlock3.compose.ui.sge

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import warlockfe.warlock3.core.prefs.models.AccountEntity

/**
 * The first step of the SGE connect wizard: a list of saved accounts to pick from (logging in with
 * the stored password), plus an "Add account" option that opens the username/password form. Falls
 * straight through to the form when there are no saved accounts yet.
 */
@Composable
fun SgeAccountSelectorView(
    accounts: List<AccountEntity>,
    onAccountSelect: (AccountEntity) -> Unit,
    onSaveAccount: (AccountEntity) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Start on the entry form when there are no saved accounts so first-time users don't see an
    // empty list. The username pre-fills the form when an account has no saved password.
    var showForm by remember { mutableStateOf(accounts.isEmpty()) }
    var prefillUsername by remember { mutableStateOf<String?>(null) }

    if (showForm) {
        AccountsView(
            initialUsername = prefillUsername,
            initialPassword = null,
            onAccountSelect = onSaveAccount,
            onCancel = {
                if (accounts.isEmpty()) {
                    onCancel()
                } else {
                    showForm = false
                    prefillUsername = null
                }
            },
            modifier = modifier,
        )
    } else {
        Column(modifier = modifier.fillMaxSize()) {
            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                itemsIndexed(accounts) { index, account ->
                    ListItem(
                        modifier =
                            Modifier.clickable {
                                if (account.password.isNullOrEmpty()) {
                                    // No saved password; open the form to enter one.
                                    prefillUsername = account.username
                                    showForm = true
                                } else {
                                    onAccountSelect(account)
                                }
                            },
                        headlineContent = { Text(account.username) },
                    )
                    if (index < accounts.size - 1) {
                        HorizontalDivider()
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.End),
            ) {
                OutlinedButton(onClick = onCancel) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        prefillUsername = null
                        showForm = true
                    },
                ) {
                    Text("Add account")
                }
            }
        }
    }
}
