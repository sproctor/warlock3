package warlockfe.warlock3.compose.desktop.ui.game

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.jewel.ui.component.MenuScope
import warlockfe.warlock3.compose.desktop.shim.WarlockMenuButton
import warlockfe.warlock3.compose.generated.resources.Res
import warlockfe.warlock3.compose.generated.resources.more_horiz

/**
 * The "..." overflow button used by the per-window header and the window-list rows. Renders the
 * [more_horiz] icon and opens a Jewel popup menu of [content]. [content] is the usual [MenuScope]
 * builder given a `dismiss` lambda, so item handlers can close the menu before acting (Jewel menu
 * items do not self-dismiss). [horizontalAlignment] anchors the menu to the button. Backed by the
 * shared [WarlockMenuButton].
 */
@Composable
fun WindowMenuButton(
    tint: Color,
    horizontalAlignment: Alignment.Horizontal,
    modifier: Modifier = Modifier,
    content: MenuScope.(dismiss: () -> Unit) -> Unit,
) {
    WarlockMenuButton(
        modifier = modifier,
        horizontalAlignment = horizontalAlignment,
        anchor = { toggle ->
            Image(
                modifier =
                    Modifier
                        .size(18.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { toggle() }
                        .padding(2.dp),
                painter = painterResource(Res.drawable.more_horiz),
                colorFilter = ColorFilter.tint(tint),
                contentDescription = "Window menu",
            )
        },
        menuContent = content,
    )
}
