package warlockfe.warlock3.compose.desktop.shim

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField

@Composable
fun WarlockTextField(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    placeholder: String? = null,
) {
    TextField(
        state = state,
        modifier = modifier,
        enabled = enabled,
        readOnly = readOnly,
        placeholder = placeholder?.let { @Composable { Text(it) } },
    )
}
