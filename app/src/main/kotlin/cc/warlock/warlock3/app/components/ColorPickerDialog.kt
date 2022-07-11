package cc.warlock.warlock3.app.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogState
import cc.warlock.warlock3.app.util.toColor
import cc.warlock.warlock3.app.util.toHexString
import cc.warlock.warlock3.app.util.toWarlockColor
import cc.warlock.warlock3.core.text.WarlockColor
import cc.warlock.warlock3.core.util.toWarlockColor
import com.godaddy.android.colorpicker.ClassicColorPicker
import com.godaddy.android.colorpicker.HsvColor

@Composable
fun ColorPickerDialog(
    initialColor: Color?,
    state: DialogState = DialogState(size = DpSize(300.dp, height = 400.dp)),
    onCloseRequest: () -> Unit,
    onColorSelected: (color: WarlockColor?) -> Unit,
) {
    var currentColor by remember { mutableStateOf(initialColor?.let { HsvColor.from(it) }) }
    Dialog(
        title = "Choose color",
        state = state,
        onCloseRequest = onCloseRequest,
    ) {
        Column(Modifier.fillMaxSize().padding(8.dp)) {
            ClassicColorPicker(
                modifier = Modifier.padding(bottom = 8.dp).weight(1f),
                color = initialColor ?: Color.Unspecified,
            ) { color ->
                currentColor = color
            }
            Button(onClick = { onColorSelected(currentColor?.toColor()?.toWarlockColor()) }) {
                Text("OK")
            }
        }
    }
}