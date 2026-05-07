package warlockfe.warlock3.compose.desktop.ui.window

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.theme.JewelTheme

@Composable
fun DesktopDropIndicator(
    isVertical: Boolean,
    modifier: Modifier = Modifier,
) {
    val color = JewelTheme.globalColors.outlines.focused
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
