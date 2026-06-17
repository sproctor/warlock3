package warlockfe.warlock3.compose.desktop.shim

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import org.jetbrains.jewel.ui.component.EditableComboBox
import org.jetbrains.jewel.ui.component.PopupManager
import org.jetbrains.jewel.ui.component.SimpleListItem

/**
 * An editable text field with an attached dropdown of suggestions: the user can type any value or
 * pick one of [items] from the chevron popup. The field's text lives in [state]; [onSelect]
 * fires (in addition to the text being set) when an item is chosen, so callers can react to a full
 * selection (e.g. also fill a related field).
 */
@Composable
fun <T> WarlockEditableTextDropdown(
    state: TextFieldState,
    items: List<T>,
    modifier: Modifier = Modifier,
    itemLabel: (T) -> String = { it.toString() },
    onSelect: (T) -> Unit = {},
) {
    val popupManager = remember { PopupManager() }
    EditableComboBox(
        textFieldState = state,
        modifier = modifier,
        popupManager = popupManager,
        popupContent = {
            Column(modifier = Modifier.fillMaxWidth()) {
                items.forEach { item ->
                    SimpleListItem(
                        text = itemLabel(item),
                        selected = false,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelect(item)
                                    popupManager.setPopupVisible(false)
                                },
                    )
                }
            }
        },
    )
}
