package warlockfe.warlock3.compose.desktop.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.jewel.foundation.theme.LocalContentColor
import org.jetbrains.jewel.ui.component.Text
import warlockfe.warlock3.compose.desktop.shim.WarlockDialog
import warlockfe.warlock3.compose.desktop.shim.WarlockOutlinedButton
import warlockfe.warlock3.compose.util.toColor
import warlockfe.warlock3.core.text.WarlockColor

/**
 * A text-color picker with the skin's named palette on top: clicking a palette swatch stores a skin
 * reference (the color tracks the skin), while "Custom color" opens the standard picker and stores a
 * frozen literal. See the spec's skin-ref vs literal distinction.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DesktopColorRefPickerDialog(
    palette: Map<String, WarlockColor>,
    onSelectSlot: (String) -> Unit,
    onSelectCustom: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    WarlockDialog(
        title = "Text color",
        onCloseRequest = onClose,
        width = 420.dp,
        height = 360.dp,
    ) {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Skin palette", color = LocalContentColor.current.copy(alpha = 0.6f))
            Text("Tracks the skin - follows a skin change", color = LocalContentColor.current.copy(alpha = 0.6f))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                palette.toSortedMap().forEach { (slot, color) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .border(1.dp, Color.Gray.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                .background(color.toColor())
                                .clickable {
                                    onSelectSlot(slot)
                                    onClose()
                                },
                        )
                        Text(slot, fontSize = 9.sp)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            WarlockOutlinedButton(
                onClick = {
                    onSelectCustom()
                    onClose()
                },
                text = "Custom color (frozen)...",
            )
        }
    }
}
