package warlockfe.warlock3.compose.desktop.shim

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import org.jetbrains.jewel.ui.component.ListComboBox

@Composable
fun <T> WarlockDropdownSelect(
    items: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    itemLabelBuilder: (T) -> String = { it.toString() },
) {
    val labels = remember(items) { items.map(itemLabelBuilder) }
    val selectedIndex = items.indexOf(selected).coerceAtLeast(0)
    ListComboBox(
        items = labels,
        selectedIndex = selectedIndex,
        onSelectedItemChange = { idx ->
            onSelect(items[idx])
        },
        modifier = modifier,
    )
}
