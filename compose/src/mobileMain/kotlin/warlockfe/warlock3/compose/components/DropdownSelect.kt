package warlockfe.warlock3.compose.components

import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import warlockfe.warlock3.compose.shim.dropdownSelectedIndex

/**
 * The Material dropdown, with an optional floating [label]. Shared screens use
 * [warlockfe.warlock3.compose.shim.WarlockDropdownSelect], which draws through this with no label;
 * the label is what the mobile-only screens that call this directly need.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T> DropdownSelect(
    items: List<T>,
    selected: T?,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    itemLabelBuilder: (T) -> String = { it.toString() },
    label: @Composable (() -> Unit)? = null,
    textStyle: TextStyle? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedIndex = dropdownSelectedIndex(items, selected)
    val selectedLabel = if (selectedIndex >= 0) itemLabelBuilder(items[selectedIndex]) else ""
    ExposedDropdownMenuBox(
        modifier = modifier,
        expanded = expanded,
        onExpandedChange = {
            expanded = it
        },
    ) {
        OutlinedTextField(
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            readOnly = true,
            value = selectedLabel,
            onValueChange = { },
            label = label,
            textStyle = textStyle ?: LocalTextStyle.current,
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
            },
        ) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = {
                        Text(text = itemLabelBuilder(item))
                    },
                    onClick = {
                        onSelect(item)
                        expanded = false
                    },
                )
            }
        }
    }
}
