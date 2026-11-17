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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> DropdownSelect(
    items: List<T>,
    selected: T?,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    itemLabelBuilder: (T) -> String = { it.toString() },
    label: @Composable (() -> Unit)? = null,
    textStyle: TextStyle? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    // A null [selected] that is not itself one of the items shows nothing selected; null is a real
    // item where T is nullable (the character selector's "Global").
    val selectedIndex = items.indexOf(selected)
    val selectedLabel =
        when {
            selectedIndex >= 0 -> itemLabelBuilder(items[selectedIndex])
            selected != null -> itemLabelBuilder(selected)
            else -> ""
        }
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
