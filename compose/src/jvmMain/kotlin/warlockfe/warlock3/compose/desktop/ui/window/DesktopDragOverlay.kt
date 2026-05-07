package warlockfe.warlock3.compose.desktop.ui.window

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import warlockfe.warlock3.compose.ui.window.DragDropState

@Composable
fun DesktopDragOverlay(
    dragDropState: DragDropState,
    modifier: Modifier = Modifier,
) {
    val item = dragDropState.draggedItem ?: return

    val shape = RoundedCornerShape(2.dp)
    Box(
        modifier =
            modifier
                .zIndex(Float.MAX_VALUE)
                .graphicsLayer {
                    translationX = dragDropState.dragOffset.x - 100.dp.toPx()
                    translationY = dragDropState.dragOffset.y - 12.dp.toPx()
                    alpha = 0.85f
                }.shadow(4.dp, shape = shape)
                .background(
                    color = JewelTheme.globalColors.panelBackground,
                    shape = shape,
                ).border(
                    width = Dp.Hairline,
                    color = JewelTheme.globalColors.borders.normal,
                    shape = shape,
                ).width(200.dp)
                .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = item.name,
            color = JewelTheme.globalColors.text.normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
