package warlockfe.warlock3.compose.desktop.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.PopupMenu
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.separator
import org.jetbrains.jewel.ui.theme.menuStyle
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
                ActionNavMenuItem(
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
                    ActionNavMenuItem(
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

/** A menu row that navigates within the popup (drill in/out) without closing it. */
@Composable
private fun ActionNavMenuItem(
    label: String,
    onClick: () -> Unit,
    leading: String? = null,
    trailing: String? = null,
) {
    val style = JewelTheme.menuStyle
    val itemColors = style.colors.itemColors
    val itemMetrics = style.metrics.itemMetrics
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            if (interaction is HoverInteraction.Enter) {
                focusRequester.requestFocus()
            }
        }
    }
    val background =
        when {
            pressed -> itemColors.backgroundPressed
            hovered -> itemColors.backgroundHovered
            else -> itemColors.background
        }
    val contentColor =
        when {
            pressed -> itemColors.contentPressed
            hovered -> itemColors.contentHovered
            else -> itemColors.content
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .background(background, RoundedCornerShape(itemMetrics.selectionCornerSize))
                .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
                .defaultMinSize(minHeight = itemMetrics.minHeight)
                .padding(itemMetrics.contentPadding),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            Text(text = leading, color = contentColor)
        }
        Text(
            modifier = Modifier.weight(1f),
            text = label,
            color = contentColor,
        )
        if (trailing != null) {
            Text(text = trailing, color = contentColor)
        }
    }
}
