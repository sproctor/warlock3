package warlockfe.warlock3.compose.ui.window

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun DropIndicator(
    isVertical: Boolean,
    modifier: Modifier = Modifier,
) {
    val color = MaterialTheme.colorScheme.primary
    if (isVertical) {
        Box(
            modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(color),
        )
    } else {
        Box(
            modifier
                .fillMaxHeight()
                .width(2.dp)
                .background(color),
        )
    }
}
