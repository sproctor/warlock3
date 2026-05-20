package warlockfe.warlock3.compose.ui.window

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.Dp

@Composable
fun DialogButton(
    onClick: () -> Unit,
    shape: Shape,
    background: (isHovered: Boolean, isPressed: Boolean) -> Brush,
    border: (isHovered: Boolean, isPressed: Boolean) -> Brush,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.(isHovered: Boolean, isPressed: Boolean) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    Box(
        modifier =
            modifier
                .background(background(isHovered, isPressed), shape)
                .border(width = Dp.Hairline, brush = border(isHovered, isPressed), shape = shape)
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ),
    ) {
        content(isHovered, isPressed)
    }
}
