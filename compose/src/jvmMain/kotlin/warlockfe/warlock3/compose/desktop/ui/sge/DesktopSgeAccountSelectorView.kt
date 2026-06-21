package warlockfe.warlock3.compose.desktop.ui.sge

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.Text
import warlockfe.warlock3.compose.desktop.shim.WarlockButton
import warlockfe.warlock3.compose.desktop.shim.WarlockOutlinedButton
import warlockfe.warlock3.compose.desktop.shim.WarlockScrollableColumn
import warlockfe.warlock3.core.prefs.models.AccountEntity

/**
 * The first step of the SGE connect wizard: a list of saved accounts to pick from (logging in with
 * the stored password), plus an "Add account" option that opens the username/password form. Falls
 * straight through to the form when there are no saved accounts yet.
 */
@Composable
fun DesktopSgeAccountSelectorView(
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
        DesktopAccountsView(
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
            WarlockScrollableColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                accounts.forEachIndexed { index, account ->
                    Text(
                        text = account.username,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (account.password.isNullOrEmpty()) {
                                        // No saved password; open the form to enter one.
                                        prefillUsername = account.username
                                        showForm = true
                                    } else {
                                        onAccountSelect(account)
                                    }
                                }.padding(horizontal = 12.dp, vertical = 12.dp),
                    )
                    if (index < accounts.size - 1) {
                        Divider(orientation = Orientation.Horizontal, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                WarlockOutlinedButton(onClick = onCancel, text = "Cancel")
                WarlockButton(
                    onClick = {
                        prefillUsername = null
                        showForm = true
                    },
                    text = "Add account",
                )
            }
        }
    }
}
