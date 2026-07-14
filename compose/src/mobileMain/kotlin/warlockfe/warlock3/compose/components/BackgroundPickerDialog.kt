package warlockfe.warlock3.compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import warlockfe.warlock3.compose.util.toColor
import warlockfe.warlock3.core.text.Background
import warlockfe.warlock3.core.text.toHexString

/**
 * The tri-state background picker (mobile twin of DesktopBackgroundPickerDialog): three explicit,
 * selectable states rather than a color-or-clear control. Inherit is annotated with what it falls back
 * to, so it stays distinguishable from None. Color opens the standard color picker.
 */
@Composable
fun BackgroundPickerDialog(
    current: Background,
    inheritedBackground: Background,
    onSelect: (Background) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pickingColor by remember { mutableStateOf(false) }
    if (pickingColor) {
        ColorPickerDialog(
            initialColor = (current as? Background.Fill)?.color.toColor(default = Color.Gray),
            onCloseRequest = { pickingColor = false },
            onColorSelect = {
                onSelect(Background.Fill(it))
                onClose()
            },
        )
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Background") },
        confirmButton = { TextButton(onClick = onClose) { Text("Close") } },
        text = {
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
            }
        },
    )
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
                        Modifier.border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
                    } else {
                        Modifier
                    },
                ).clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
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
