package warlockfe.warlock3.compose.desktop.shim

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import org.jetbrains.jewel.foundation.lazy.SelectionMode
import org.jetbrains.jewel.foundation.lazy.rememberSelectableLazyListState
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.ListComboBox

@Composable
fun <T> WarlockDropdownSelect(
    items: List<T>,
    selected: T?,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = JewelTheme.defaultTextStyle,
    itemLabelBuilder: (T) -> String = { it.toString() },
) {
    val labels = remember(items) { items.map(itemLabelBuilder) }
    // ListComboBox shows an empty label for index -1, which is how a caller says nothing is selected
    // - a panel dropdown whose game-sent selection names no option, say. Only a null [selected] that
    // is not itself one of the items means that: null is a real item where T is nullable (the
    // character selector's "Global"), and a non-null one that has since left the list still falls
    // back to the first, as it always has.
    val selectedIndex =
        items.indexOfFirst { it == selected }.let { index ->
            if (index < 0 && selected != null) 0 else index
        }
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
        textStyle = textStyle,
    )
}
