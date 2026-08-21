package warlockfe.warlock3.compose.desktop.ui.game

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.jewel.ui.component.PopupMenu
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.separator
import warlockfe.warlock3.compose.desktop.shim.WarlockNavMenuItem
import warlockfe.warlock3.compose.desktop.shim.WarlockOutlinedButton
import warlockfe.warlock3.core.prefs.models.Action
import kotlin.uuid.Uuid

/**
 * A single action button on the desktop game screen. A leaf runs its script on click; a group opens a
 * drill-down [PopupMenu] of the actions it references (resolved against [pool]).
 */
@Composable
fun DesktopActionButton(
    action: Action,
    pool: Map<Uuid, Action>,
    onRunLeaf: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        WarlockOutlinedButton(
            onClick = {
                if (action.isGroup) expanded = true else onRunLeaf(action)
            },
            text = action.name.ifBlank { "(unnamed)" },
        )
        if (expanded) {
            ActionDrillDownPopup(
                root = action,
                pool = pool,
                onRunLeaf = onRunLeaf,
                onDismiss = { expanded = false },
            )
        }
    }
}

@Composable
private fun ActionDrillDownPopup(
    root: Action,
    pool: Map<Uuid, Action>,
    onRunLeaf: (Action) -> Unit,
    onDismiss: () -> Unit,
) {
    // Nested popups grab focus on desktop, so we navigate within one popup: each group drills in, the
    // back row returns a level. The path holds the groups we've drilled through.
    var path by remember(root.id) { mutableStateOf<List<Action>>(emptyList()) }
    PopupMenu(
        onDismissRequest = {
            onDismiss()
            true
        },
        horizontalAlignment = Alignment.Start,
    ) {
        val current = path.lastOrNull() ?: root
        // Skip any child that is an ancestor on the path, so a reference cycle can't loop forever.
        val ancestors = path.mapTo(mutableSetOf()) { it.id }
        val children = current.children.mapNotNull { pool[it] }.filterNot { it.id in ancestors }
        if (path.isNotEmpty()) {
            passiveItem {
                WarlockNavMenuItem(
                    label = current.name.ifBlank { "(unnamed)" },
                    leading = "\u2190",
                    onClick = { path = path.dropLast(1) },
                )
            }
            separator()
        }
        children.forEach { child ->
            if (child.isGroup) {
                passiveItem {
                    WarlockNavMenuItem(
                        label = child.name.ifBlank { "(unnamed)" },
                        trailing = "\u203A",
                        onClick = { path = path + child },
                    )
                }
            } else {
                selectableItem(
                    selected = false,
                    onClick = {
                        onRunLeaf(child)
                        onDismiss()
                    },
                ) {
                    Text(child.name.ifBlank { "(unnamed)" })
                }
            }
        }
    }
}
