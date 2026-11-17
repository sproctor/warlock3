package warlockfe.warlock3.compose.shim

import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import warlockfe.warlock3.compose.components.CheckboxRow
import warlockfe.warlock3.compose.components.DropdownSelect

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
    Text(
        text,
        modifier = modifier,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
actual fun WarlockButton(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier,
    enabled: Boolean,
) {
    Button(onClick = onClick, modifier = modifier, enabled = enabled) {
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
    OutlinedButton(onClick = onClick, modifier = modifier, enabled = enabled) {
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
actual fun <T> WarlockDropdownSelect(
    items: List<T>,
    selected: T?,
    onSelect: (T) -> Unit,
    modifier: Modifier,
    itemLabelBuilder: (T) -> String,
    textStyle: TextStyle?,
) {
    DropdownSelect(
        items = items,
        selected = selected,
        onSelect = onSelect,
        modifier = modifier,
        itemLabelBuilder = itemLabelBuilder,
        textStyle = textStyle,
    )
}
