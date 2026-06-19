package warlockfe.warlock3.compose.desktop.ui.game

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.jewel.ui.component.MenuScope
import org.jetbrains.jewel.ui.component.PopupMenu
import warlockfe.warlock3.compose.generated.resources.Res
import warlockfe.warlock3.compose.generated.resources.more_horiz

/**
 * The "..." overflow button used by the per-window header and the window-list rows. Renders the
 * [more_horiz] icon and opens a Jewel [PopupMenu] of [content] (a [MenuScope], so callers fill it
 * with `selectableItem`/`separator`). [horizontalAlignment] anchors the menu to the button.
 */
@Composable
fun WindowMenuButton(
    tint: Color,
    horizontalAlignment: Alignment.Horizontal,
    modifier: Modifier = Modifier,
    // A Jewel MenuScope builder block (not composable itself): fill it with selectableItem/separator.
    content: MenuScope.() -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Image(
            modifier =
                Modifier
                    .size(18.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { open = true }
                    .padding(2.dp),
            painter = painterResource(Res.drawable.more_horiz),
            colorFilter = ColorFilter.tint(tint),
            contentDescription = "Window menu",
        )
        if (open) {
            PopupMenu(
                onDismissRequest = {
                    open = false
                    true
                },
                horizontalAlignment = horizontalAlignment,
                content = content,
            )
        }
    }
}
