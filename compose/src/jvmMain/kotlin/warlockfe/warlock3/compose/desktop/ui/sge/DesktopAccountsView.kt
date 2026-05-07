package warlockfe.warlock3.compose.desktop.ui.sge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.ui.component.Text
import warlockfe.warlock3.compose.desktop.shim.WarlockButton
import warlockfe.warlock3.compose.desktop.shim.WarlockOutlinedButton
import warlockfe.warlock3.compose.desktop.shim.WarlockSecureTextField
import warlockfe.warlock3.compose.desktop.shim.WarlockTextField
import warlockfe.warlock3.core.prefs.models.AccountEntity

@Composable
fun DesktopAccountsView(
    initialUsername: String?,
    initialPassword: String?,
    onAccountSelect: (AccountEntity) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val username = rememberTextFieldState(initialUsername ?: "")
    val password = rememberTextFieldState(initialPassword ?: "")

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Column(
                modifier = Modifier.align(Alignment.Center),
            ) {
                Text("Username")
                Spacer(Modifier.height(4.dp))
                WarlockTextField(
                    modifier = Modifier.padding(8.dp).width(280.dp),
                    state = username,
                )
                Spacer(Modifier.height(8.dp))
                Text("Password")
                Spacer(Modifier.height(4.dp))
                WarlockSecureTextField(
                    modifier = Modifier.padding(8.dp).width(280.dp),
                    state = password,
                )
            }
        }
        Row(
            modifier =
                Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            WarlockOutlinedButton(onClick = onCancel, text = "Cancel")
            WarlockButton(
                onClick = {
                    onAccountSelect(
                        AccountEntity(
                            username.text.toString().trim(),
                            password.text.toString().trim(),
                        ),
                    )
                },
                text = "Next",
            )
        }
    }
}
