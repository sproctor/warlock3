package warlockfe.warlock3.compose.desktop.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import warlockfe.warlock3.compose.generated.resources.Res
import warlockfe.warlock3.compose.generated.resources.palette

@Composable
fun DesktopStylePreview(
    textColor: Color,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
) {
    Box(
        modifier =
            modifier
                .size(size)
                .background(
                    color = backgroundColor,
                    shape = RoundedCornerShape(4.dp),
                ).border(
                    1.dp,
                    textColor.takeOrElse { JewelTheme.globalColors.borders.normal },
                    RoundedCornerShape(4.dp),
                ),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(Res.drawable.palette),
            contentDescription = "Color preview",
            modifier = Modifier.size(size * 0.625f),
            colorFilter = ColorFilter.tint(textColor.takeOrElse { JewelTheme.globalColors.text.normal }),
        )
    }
}
