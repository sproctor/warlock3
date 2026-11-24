package warlockfe.warlock3.compose.shim

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import warlockfe.warlock3.compose.components.DropdownSelect

actual val warlockContentColor: Color
    @Composable get() = LocalContentColor.current

actual val disclosureArrowSize: Dp = 20.dp

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
    Row(
        modifier =
            modifier.toggleable(
                value = checked,
                onValueChange = onCheckedChange,
                role = Role.Checkbox,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Spacer(Modifier.width(16.dp))
        Text(text)
    }
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

@Composable
actual fun WarlockTextField(
    state: TextFieldState,
    modifier: Modifier,
    enabled: Boolean,
    readOnly: Boolean,
    placeholder: String?,
) {
    TextField(
        state = state,
        modifier = modifier,
        enabled = enabled,
        readOnly = readOnly,
        label = placeholder?.let { @Composable { Text(it) } },
        lineLimits = TextFieldLineLimits.SingleLine,
    )
}
