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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.jewel.ui.component.Text
import warlockfe.warlock3.compose.generated.resources.Res
import warlockfe.warlock3.compose.generated.resources.front_hand
import warlockfe.warlock3.compose.generated.resources.star_shine
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
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DesktopHandBox(
            icon = {
                Image(
                    modifier = Modifier.size(18.dp).rotate(90f).mirror(),
                    painter = painterResource(Res.drawable.front_hand),
                    colorFilter = ColorFilter.tint(WarlockGameChrome.textFaint),
                    contentDescription = "Left hand",
                )
            },
            value = left,
            valueColor = WarlockGameChrome.textPrimary,
        )
        DesktopHandBox(
            icon = {
                Image(
                    modifier = Modifier.size(18.dp).rotate(-90f),
                    painter = painterResource(Res.drawable.front_hand),
                    colorFilter = ColorFilter.tint(WarlockGameChrome.textFaint),
                    contentDescription = "Right hand",
                )
            },
            value = right,
            valueColor = WarlockGameChrome.textPrimary,
        )
        DesktopHandBox(
            icon = {
                Image(
                    modifier = Modifier.size(18.dp),
                    painter = painterResource(Res.drawable.star_shine),
                    colorFilter = ColorFilter.tint(WarlockGameChrome.spellIcon),
                    contentDescription = "Spell",
                )
            },
            value = spell,
            valueColor = WarlockGameChrome.spellText,
        )
    }
}

@Composable
fun RowScope.DesktopHandBox(
    icon: @Composable () -> Unit,
    value: String?,
    valueColor: Color,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(6.dp)
    val isEmpty = value.isNullOrBlank()
    Row(
        modifier =
            modifier
                .weight(1f)
                .background(color = WarlockGameChrome.panelAlt, shape = shape)
                .border(width = 1.dp, color = WarlockGameChrome.border, shape = shape)
                .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (isEmpty) "empty" else value,
            color = if (isEmpty) WarlockGameChrome.caption else valueColor,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
