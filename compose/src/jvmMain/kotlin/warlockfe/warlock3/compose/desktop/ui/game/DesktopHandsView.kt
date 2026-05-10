package warlockfe.warlock3.compose.desktop.ui.game

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import warlockfe.warlock3.compose.generated.resources.Res
import warlockfe.warlock3.compose.generated.resources.front_hand
import warlockfe.warlock3.compose.generated.resources.wand_stars
import warlockfe.warlock3.compose.util.mirror

@Composable
fun DesktopHandsView(
    left: String?,
    right: String?,
    spell: String?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        DesktopHandBox(
            icon = {
                Image(
                    modifier = Modifier.rotate(90f).mirror(),
                    painter = painterResource(Res.drawable.front_hand),
                    colorFilter = ColorFilter.tint(JewelTheme.globalColors.text.normal),
                    contentDescription = "Left hand",
                )
            },
            value = left ?: "",
        )
        DesktopHandBox(
            icon = {
                Image(
                    modifier = Modifier.rotate(-90f),
                    painter = painterResource(Res.drawable.front_hand),
                    colorFilter = ColorFilter.tint(JewelTheme.globalColors.text.normal),
                    contentDescription = "Right hand",
                )
            },
            value = right ?: "",
        )
        DesktopHandBox(
            icon = {
                Image(
                    painter = painterResource(Res.drawable.wand_stars),
                    colorFilter = ColorFilter.tint(JewelTheme.globalColors.text.normal),
                    contentDescription = "Spell",
                )
            },
            value = spell ?: "",
        )
    }
}

@Composable
fun RowScope.DesktopHandBox(
    icon: @Composable () -> Unit,
    value: String,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(4.dp)
    Row(
        modifier =
            modifier
                .weight(1f)
                .background(color = JewelTheme.globalColors.panelBackground, shape = shape)
                .border(width = 1.dp, color = JewelTheme.globalColors.borders.normal, shape = shape)
                .padding(4.dp),
    ) {
        icon()
        Spacer(Modifier.width(8.dp))
        Text(
            text = value,
            maxLines = 1,
        )
    }
}
