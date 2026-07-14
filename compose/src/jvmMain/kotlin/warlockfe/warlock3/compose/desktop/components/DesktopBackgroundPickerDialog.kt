package warlockfe.warlock3.compose.desktop.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.theme.LocalContentColor
import org.jetbrains.jewel.ui.component.Text
import warlockfe.warlock3.compose.components.inheritedBackgroundLabel
import warlockfe.warlock3.compose.desktop.shim.WarlockDialog
import warlockfe.warlock3.compose.util.toColor
import warlockfe.warlock3.core.text.Background
import warlockfe.warlock3.core.text.WarlockColor
import warlockfe.warlock3.core.text.toHexString

/**
 * The tri-state background picker: three explicit, selectable states rather than a color-or-clear
 * control. Inherit is annotated with what it falls back to, so it is distinguishable from None (which
 * looks identical in game but does not inherit a later global background). Color opens the standard
 * color picker. Applies the chosen [Background] and closes.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DesktopBackgroundPickerDialog(
    current: Background,
    inheritedBackground: Background,
    onSelect: (Background) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    palette: Map<String, WarlockColor> = emptyMap(),
    onSelectSlot: (String) -> Unit = {},
) {
    var pickingColor by remember { mutableStateOf(false) }
    if (pickingColor) {
        DesktopColorPickerDialog(
            initialColor = (current as? Background.Fill)?.color.toColor(default = Color.Gray),
            onCloseRequest = { pickingColor = false },
            onColorSelect = {
                onSelect(Background.Fill(it))
                onClose()
            },
        )
    }
    WarlockDialog(
        title = "Background",
        onCloseRequest = onClose,
        width = 380.dp,
        height = 260.dp,
    ) {
        Column(modifier.fillMaxWidth()) {
            OptionRow(
                selected = current == Background.Unset,
                title = "Inherit",
                subtitle = inheritedBackgroundLabel(inheritedBackground),
                onClick = {
                    onSelect(Background.Unset)
                    onClose()
                },
            )
            OptionRow(
                selected = current == Background.None,
                title = "None",
                subtitle = "transparent - shows the window background",
                onClick = {
                    onSelect(Background.None)
                    onClose()
                },
            )
            OptionRow(
                selected = current is Background.Fill,
                title = "Color",
                subtitle = (current as? Background.Fill)?.color?.toHexString() ?: "pick a color",
                onClick = { pickingColor = true },
                swatch = (current as? Background.Fill)?.color.toColor(default = Color.Transparent),
            )
            if (palette.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Skin palette - tracks the skin",
                    modifier = Modifier.padding(horizontal = 10.dp),
                    color = LocalContentColor.current.copy(alpha = 0.6f),
                )
                FlowRow(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    palette.toSortedMap().forEach { (slot, color) ->
                        Box(
                            Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .border(1.dp, Color.Gray.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                .background(color.toColor())
                                .clickable {
                                    onSelectSlot(slot)
                                    onClose()
                                },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionRow(
    selected: Boolean,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    swatch: Color? = null,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .then(
                    if (selected) {
                        Modifier.border(1.dp, LocalContentColor.current.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                    } else {
                        Modifier
                    },
                ).clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title)
            Text(subtitle, color = LocalContentColor.current.copy(alpha = 0.6f))
        }
        if (swatch != null) {
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .border(1.dp, Color.Gray.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                    .background(swatch),
            )
        }
    }
}
