package cc.warlock.warlock3.app.view

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import cc.warlock.warlock3.app.viewmodel.GameViewModel
import cc.warlock.warlock3.core.StyledString
import cc.warlock.warlock3.core.StyledStringLeaf
import cc.warlock.warlock3.core.WarlockColor

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun GameView(viewModel: GameViewModel) {
    val lines by viewModel.lines.collectAsState()
    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) {
            items(lines) { line ->
                DisplayStyledString(line)
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            val textState = remember { mutableStateOf("") }
            OutlinedTextField(
                value = textState.value,
                modifier = Modifier
                    .weight(1f)
                    .onKeyEvent { event ->
                        if (event.key.keyCode == Key.Enter.keyCode) {
                            viewModel.send(textState.value)
                            textState.value = ""
                            true
                        } else {
                            false
                        }
                    },
                onValueChange = { textState.value = it },
            )
            Button(
                onClick = {
                    viewModel.send(textState.value)
                    textState.value = ""
                }
            ) {
                Text("SEND")
            }
        }
    }
}

@Composable
fun DisplayStyledString(value: StyledString) {
    Row {
        value.substrings.forEach { DisplayStyledStringLeaf(it) }
    }
}

@Composable
fun DisplayStyledStringLeaf(value: StyledStringLeaf) {
    Text(
        modifier = Modifier,
        color = value.style?.textColor?.toColor() ?: Color.Unspecified,
        text = value.text,
        fontFamily = if (value.style?.monospace == true) FontFamily.Monospace else null
    )
}

fun WarlockColor.toColor(): Color {
    if (this == WarlockColor.default) {
        return Color.Unspecified
    }
    return Color(red = red, green = green, blue = blue)
}