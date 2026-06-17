package warlockfe.warlock3.compose.desktop.shim

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.jewel.ui.component.MenuScope
import org.jetbrains.jewel.ui.component.PopupMenu

/**
 * Opens a Jewel popup menu from an [anchor]. The anchor is given a `toggle` callback to wire to its
 * own click handler, so it works whether the anchor is a plain element or an interactive control
 * (a button, which would otherwise consume the click before a wrapper could see it).
 *
 * [menuContent] is the usual Jewel [MenuScope] builder, with a `dismiss` lambda passed in so item
 * handlers can close the menu before acting
 * (e.g. `selectableItem(false, onClick = { dismiss(); doThing() }) { Text(...) }`).
 */
@Composable
fun WarlockMenuButton(
    anchor: @Composable (toggle: () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.End,
    menuContent: MenuScope.(dismiss: () -> Unit) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        anchor { expanded = !expanded }
        if (expanded) {
            PopupMenu(
                onDismissRequest = {
                    expanded = false
                    true
                },
                horizontalAlignment = horizontalAlignment,
            ) {
                menuContent { expanded = false }
            }
        }
    }
}
