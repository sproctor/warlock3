package warlockfe.warlock3.compose.shim

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.jetbrains.jewel.foundation.theme.LocalContentColor
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text

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
