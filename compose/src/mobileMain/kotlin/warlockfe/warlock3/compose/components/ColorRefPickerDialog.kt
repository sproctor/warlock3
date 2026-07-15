package warlockfe.warlock3.compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import warlockfe.warlock3.compose.util.toColor
import warlockfe.warlock3.core.text.WarlockColor

/**
 * A text-color picker with the skin's named palette on top (mobile twin of DesktopColorRefPickerDialog):
 * a palette swatch stores a skin reference (tracks the skin), while "Custom color" opens the standard
 * picker and stores a frozen literal.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorRefPickerDialog(
    palette: Map<String, WarlockColor>,
    onSelectSlot: (String) -> Unit,
    onSelectCustom: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Text color") },
        confirmButton = {
            TextButton(onClick = {
                onSelectCustom()
                onClose()
            }) { Text("Custom color...") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
        text = {
            Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Skin palette - tracks the skin",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    palette.entries.sortedBy { it.key }.forEach { (slot, color) ->
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
            }
        },
    )
}
