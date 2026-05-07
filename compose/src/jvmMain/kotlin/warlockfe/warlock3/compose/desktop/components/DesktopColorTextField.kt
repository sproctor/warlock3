package warlockfe.warlock3.compose.desktop.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import warlockfe.warlock3.compose.desktop.shim.WarlockOutlinedButton
import warlockfe.warlock3.compose.desktop.shim.WarlockTextField
import warlockfe.warlock3.compose.util.toColor
import warlockfe.warlock3.core.text.WarlockColor
import warlockfe.warlock3.core.text.specifiedOrNull
import warlockfe.warlock3.core.text.toHexString
import warlockfe.warlock3.core.util.toWarlockColor

@Composable
fun DesktopColorTextField(
    label: String,
    state: TextFieldState,
    modifier: Modifier = Modifier,
) {
    var editColor by remember { mutableStateOf<Pair<String, (WarlockColor) -> Unit>?>(null) }
    var invalidColor by remember { mutableStateOf(false) }
    LaunchedEffect(state) {
        snapshotFlow { state.text.toString() }
            .collectLatest {
                invalidColor = it.toWarlockColor() == null && it.isNotEmpty()
            }
    }
    Column(modifier = modifier) {
        Text(
            text = if (invalidColor) "Invalid color string" else label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            val currentColor =
                state.text
                    .toString()
                    .toWarlockColor()
                    ?.toColor()
            if (currentColor != null && currentColor.isSpecified) {
                Box(
                    Modifier
                        .size(20.dp)
                        .border(width = 1.dp, color = JewelTheme.globalColors.borders.normal)
                        .background(currentColor),
                )
                Spacer(Modifier.size(4.dp))
            }
            WarlockTextField(
                state = state,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.size(4.dp))
            WarlockOutlinedButton(
                onClick = {
                    editColor =
                        state.text.toString() to {
                            state.setTextAndPlaceCursorAtEnd(it.toHexString() ?: "")
                        }
                },
                text = "Pick",
            )
        }
    }
    editColor?.let { (colorText, setColor) ->
        val initialColor = colorText.toWarlockColor()?.specifiedOrNull()?.toColor() ?: Color.Black
        DesktopColorPickerDialog(
            initialColor = initialColor,
            onCloseRequest = { editColor = null },
            onColorSelect = { color ->
                setColor(color)
                editColor = null
            },
        )
    }
}
