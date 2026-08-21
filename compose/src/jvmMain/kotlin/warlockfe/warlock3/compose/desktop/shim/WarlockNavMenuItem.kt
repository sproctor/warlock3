package warlockfe.warlock3.compose.desktop.shim

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.theme.menuStyle

/**
 * A menu row that navigates within the popup (drill in/out) without closing it.
 *
 * Goes in a `passiveItem`, not a `selectableItem`: Jewel's selectable rows force-close the whole
 * menu on click, which would dismiss the popup instead of moving a level. That leaves this to draw
 * its own [menuStyle] background and content colours so a navigation row is indistinguishable from
 * the rows around it. [leading] and [trailing] are the arrows that say which way it goes.
 */
@Composable
internal fun WarlockNavMenuItem(
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
    // Jewel's selectableItem highlights while focused and grabs focus on hover; take focus on hover
    // here too so the previously hovered selectable item doesn't stay highlighted.
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
