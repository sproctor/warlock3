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
fun DropIndicator(isVertical: Boolean) {
    val color = MaterialTheme.colorScheme.primary
    if (isVertical) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(color)
        )
    } else {
        Box(
            Modifier
                .fillMaxHeight()
                .width(2.dp)
                .background(color)
        )
    }
}
