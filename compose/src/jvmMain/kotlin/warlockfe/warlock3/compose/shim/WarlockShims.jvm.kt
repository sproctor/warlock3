package warlockfe.warlock3.compose.shim

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.theme.LocalContentColor
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField

actual val warlockContentColor: Color
    @Composable get() = LocalContentColor.current

actual val disclosureArrowSize: Dp = 16.dp

@Composable
actual fun WarlockText(
    text: String,
    modifier: Modifier,
    color: Color,
) {
    Text(text, modifier = modifier, color = color)
}

@Composable
actual fun WarlockSecondaryText(
    text: String,
    modifier: Modifier,
) {
    Text(text, modifier = modifier, color = LocalContentColor.current.copy(alpha = 0.6f))
}

@Composable
actual fun WarlockButton(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier,
    enabled: Boolean,
) {
    DefaultButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
    ) {
        Text(text)
    }
}

@Composable
actual fun WarlockOutlinedButton(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier,
    enabled: Boolean,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
    ) {
        Text(text)
    }
}

@Composable
actual fun WarlockCheckboxRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    text: String,
    modifier: Modifier,
) {
    CheckboxRow(checked = checked, onCheckedChange = onCheckedChange, text = text, modifier = modifier)
}

@Composable
actual fun WarlockTextField(
    state: TextFieldState,
    modifier: Modifier,
    enabled: Boolean,
    readOnly: Boolean,
    onSubmit: (() -> Unit)?,
    placeholder: String?,
) {
    TextField(
        state = state,
        modifier = modifier,
        enabled = enabled,
        readOnly = readOnly,
        keyboardOptions = if (onSubmit != null) KeyboardOptions(imeAction = ImeAction.Done) else KeyboardOptions.Default,
        onKeyboardAction = onSubmit?.let { submit -> KeyboardActionHandler { submit() } },
        placeholder = placeholder?.let { @Composable { Text(it) } },
    )
}
