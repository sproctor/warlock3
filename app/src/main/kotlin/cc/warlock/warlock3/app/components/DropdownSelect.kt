package cc.warlock.warlock3.app.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> DropdownSelect(
    items: List<T>,
    selected: T,
    itemLabelBuilder: (T) -> String = { it.toString() },
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedTextField(
            value = itemLabelBuilder(selected),
            label = label,
            onValueChange = {},
            readOnly = true,
            trailingIcon = {
                IconButton(
                    onClick = { expanded = true },
                    enabled = !expanded,
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "select",
                        Modifier.rotate(
                            if (expanded) 180f else 0f
                        ),
                    )
                }
            },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            items.forEach { item: T ->
                DropdownMenuItem(
                    onClick = {
                        onSelect(item)
                        expanded = false
                    },
                    text = {
                        Text(text = itemLabelBuilder(item))
                    }
                )
            }
        }
    }
}
