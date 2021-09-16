package cc.warlock.warlock3.app.view

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.warlock.warlock3.app.viewmodel.GameViewModel

@Composable
fun WarlockEntry(modifier: Modifier, viewModel: GameViewModel) {
    val textState = remember { mutableStateOf("") }
    WarlockEntryContent(
        modifier = modifier,
        text = textState.value,
        onChange = { textState.value = it },
        onSend = {
            viewModel.send(textState.value)
            textState.value = ""
        }
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun WarlockEntryContent(modifier: Modifier, text: String, onChange: (String) -> Unit, onSend: () -> Unit) {
    Box(
        modifier = modifier
            .border(width = 1.dp, color = Color.DarkGray, shape = MaterialTheme.shapes.small)
            .padding(4.dp)
    ) {
        val focusRequester = remember { FocusRequester() }
        BasicTextField(
            value = text,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .focusRequester(focusRequester)
                .fillMaxWidth()
                .onPreviewKeyEvent { event ->
                    if (event.key.keyCode == Key.Enter.keyCode && event.type == KeyEventType.KeyDown) {
                        onSend()
                        true
                    } else {
                        false
                    }
                },
            textStyle = TextStyle.Default.copy(fontSize = 16.sp),
            onValueChange = onChange,
            maxLines = 1,
        )
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }
    }
}

@Preview
@Composable
fun WarlockEntryPreview() {
    WarlockEntryContent(
        modifier = Modifier.fillMaxWidth().padding(2.dp),
        text = "test",
        onChange = {},
        onSend = {}
    )
}