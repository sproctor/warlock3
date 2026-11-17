package warlockfe.warlock3.compose.shim

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import org.jetbrains.jewel.foundation.lazy.SelectionMode
import org.jetbrains.jewel.foundation.lazy.rememberSelectableLazyListState
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.ListComboBox

@Composable
actual fun <T> WarlockDropdownSelect(
    items: List<T>,
    selected: T?,
    onSelect: (T) -> Unit,
    modifier: Modifier,
    itemLabelBuilder: (T) -> String,
    textStyle: TextStyle?,
) {
    val labels = remember(items) { items.map(itemLabelBuilder) }
    // ListComboBox shows an empty label for index -1, which is how nothing selected is drawn.
    val selectedIndex = dropdownSelectedIndex(items, selected)
    // ListComboBox is single-select, but its default list state is created with SelectionMode.Multiple,
    // which logs a "selectionMode does not match" warning on every recomposition. Give it a
    // single-selection state so they agree.
    val listState =
        rememberSelectableLazyListState(
            initialFirstVisibleItemIndex = selectedIndex.coerceAtLeast(0),
            selectionMode = SelectionMode.Single,
        )
    ListComboBox(
        items = labels,
        selectedIndex = selectedIndex,
        onSelectedItemChange = { idx ->
            onSelect(items[idx])
        },
        modifier = modifier,
        listState = listState,
        textStyle = textStyle ?: JewelTheme.defaultTextStyle,
    )
}
