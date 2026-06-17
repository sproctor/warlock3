package warlockfe.warlock3.compose.desktop.shim

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A password field with a Show/Hide reveal toggle, as expected of a desktop form. Masks the value
 * by default; pressing the toggle swaps to a plain field so the user can verify what they typed.
 */
@Composable
fun WarlockPasswordField(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placeholder: String? = null,
) {
    var revealed by remember { mutableStateOf(false) }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (revealed) {
            WarlockTextField(
                state = state,
                modifier = Modifier.weight(1f),
                enabled = enabled,
                placeholder = placeholder,
            )
        } else {
            WarlockSecureTextField(
                state = state,
                modifier = Modifier.weight(1f),
                enabled = enabled,
                placeholder = placeholder,
            )
        }
        Spacer(Modifier.width(8.dp))
        WarlockOutlinedButton(
            onClick = { revealed = !revealed },
            text = if (revealed) "Hide" else "Show",
            enabled = enabled,
        )
    }
}
