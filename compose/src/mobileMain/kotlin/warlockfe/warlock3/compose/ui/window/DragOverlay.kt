package warlockfe.warlock3.compose.ui.window

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

@Composable
fun DragOverlay(
    dragDropState: DragDropState,
    modifier: Modifier = Modifier,
) {
    val item = dragDropState.draggedItem ?: return

    Box(
        modifier =
            modifier
                .zIndex(Float.MAX_VALUE)
                .graphicsLayer {
                    translationX = dragDropState.dragOffset.x - 100.dp.toPx()
                    translationY = dragDropState.dragOffset.y - 12.dp.toPx()
                    alpha = 0.85f
                }.shadow(4.dp, shape = MaterialTheme.shapes.extraSmall)
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = MaterialTheme.shapes.extraSmall,
                ).width(200.dp)
                .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = item.name,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleSmall,
        )
    }
}
