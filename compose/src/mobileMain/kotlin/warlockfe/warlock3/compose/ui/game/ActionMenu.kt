package warlockfe.warlock3.compose.ui.game

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.painterResource
import warlockfe.warlock3.compose.generated.resources.Res
import warlockfe.warlock3.compose.generated.resources.arrow_right
import warlockfe.warlock3.core.prefs.models.Action
import kotlin.uuid.Uuid

/**
 * A single action chip on the mobile command bar. A leaf runs its script; a group opens a drill-down
 * [DropdownMenu] of the actions it references (resolved against [pool]).
 */
@Composable
fun ActionChip(
    action: Action,
    pool: Map<Uuid, Action>,
    onRunLeaf: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (action.isGroup) {
        var expanded by remember { mutableStateOf(false) }
        Box(modifier) {
            AssistChip(
                onClick = { expanded = true },
                label = { Text(action.name.ifBlank { "(unnamed)" }) },
                trailingIcon = {
                    Icon(painter = painterResource(Res.drawable.arrow_right), contentDescription = null)
                },
            )
            ActionDropdown(
                group = action,
                pool = pool,
                expanded = expanded,
                ancestors = setOf(action.id),
                onRunLeaf = onRunLeaf,
                onDismiss = { expanded = false },
            )
        }
    } else {
        AssistChip(
            onClick = { onRunLeaf(action) },
            label = { Text(action.name.ifBlank { "(unnamed)" }) },
            modifier = modifier,
        )
    }
}

@Composable
private fun ActionDropdown(
    group: Action,
    pool: Map<Uuid, Action>,
    expanded: Boolean,
    ancestors: Set<Uuid>,
    onRunLeaf: (Action) -> Unit,
    onDismiss: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        // Skip any child already on the path to this group so a reference cycle can't recurse forever.
        val children = group.children.mapNotNull { pool[it] }.filterNot { it.id in ancestors }
        children.forEach { child ->
            if (child.isGroup) {
                var subExpanded by remember { mutableStateOf(false) }
                Box {
                    DropdownMenuItem(
                        text = { Text(child.name.ifBlank { "(unnamed)" }) },
                        trailingIcon = {
                            Icon(painter = painterResource(Res.drawable.arrow_right), contentDescription = null)
                        },
                        onClick = { subExpanded = true },
                    )
                    ActionDropdown(
                        group = child,
                        pool = pool,
                        expanded = subExpanded,
                        ancestors = ancestors + child.id,
                        onRunLeaf = { leaf ->
                            onRunLeaf(leaf)
                            onDismiss()
                        },
                        onDismiss = { subExpanded = false },
                    )
                }
            } else {
                DropdownMenuItem(
                    text = { Text(child.name.ifBlank { "(unnamed)" }) },
                    onClick = {
                        onRunLeaf(child)
                        onDismiss()
                    },
                )
            }
        }
    }
}
