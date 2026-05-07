package warlockfe.warlock3.compose.desktop.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.oikvpqya.compose.fastscroller.ScrollbarStyle
import io.github.oikvpqya.compose.fastscroller.ThumbStyle
import io.github.oikvpqya.compose.fastscroller.TrackStyle
import org.jetbrains.jewel.foundation.theme.JewelTheme

@Composable
fun desktopScrollbarStyle(
    minimalHeight: Dp = 52.dp,
    thickness: Dp = 8.dp,
    hoverDurationMillis: Int = 300,
): ScrollbarStyle {
    val thumbColor = JewelTheme.globalColors.borders.normal
    val trackColor = JewelTheme.globalColors.panelBackground
    return ScrollbarStyle(
        minimalHeight = minimalHeight,
        thickness = thickness,
        hoverDurationMillis = hoverDurationMillis,
        thumbStyle =
            ThumbStyle(
                shape = RoundedCornerShape(4.dp),
                unhoverColor = thumbColor,
                hoverColor = JewelTheme.globalColors.borders.focused,
            ),
        trackStyle =
            TrackStyle(
                shape = RoundedCornerShape(4.dp),
                unhoverColor = trackColor,
                hoverColor = trackColor,
            ),
    )
}
