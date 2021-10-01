package cc.warlock.warlock3.app.view

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.warlock.warlock3.app.viewmodel.GameViewModel

@Composable
fun WarlockEntry(modifier: Modifier, viewModel: GameViewModel) {
    val history by viewModel.sendHistory.collectAsState()
    WarlockEntryContent(
        modifier = modifier,
        onSend = {
            viewModel.send(it)
        },
        history = history
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun WarlockEntryContent(
    modifier: Modifier,
    onSend: (String) -> Unit,
    history: List<String>,
) {
    var textField by remember { mutableStateOf(TextFieldValue()) }
    var historyPosition by remember(history) { mutableStateOf(-1) }
    Box(
        modifier = modifier
            .border(width = 1.dp, color = Color.DarkGray, shape = MaterialTheme.shapes.small)
            .padding(4.dp)
    ) {
        val focusRequester = remember { FocusRequester() }
        BasicTextField(
            value = textField,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .focusRequester(focusRequester)
                .fillMaxWidth()
                .onPreviewKeyEvent { event ->
                    when {
                        event.key.keyCode == Key.Enter.keyCode && event.type == KeyEventType.KeyDown -> {
                            onSend(textField.text)
                            textField = TextFieldValue()
                            historyPosition = -1
                            true
                        }
                        event.key.keyCode == Key.DirectionUp.keyCode && event.type == KeyEventType.KeyDown -> {
                            if (historyPosition < history.size - 1) {
                                historyPosition++
                                val text = history[historyPosition]
                                textField = TextFieldValue(text = text, selection = TextRange(text.length))
                            }
                            true
                        }
                        event.key.keyCode == Key.DirectionDown.keyCode && event.type == KeyEventType.KeyDown -> {
                            if (historyPosition > 0) {
                                historyPosition--
                                val text = history[historyPosition]
                                textField = TextFieldValue(text = text, selection = TextRange(text.length))
                            }
                            true
                        }
                        else -> false
                    }
                },
            textStyle = TextStyle.Default.copy(fontSize = 16.sp),
            onValueChange = { textField = it },
            maxLines = 1,
        )
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }
    }
}

//@Preview
//@Composable
//fun WarlockEntryPreview() {
//    WarlockEntryContent(
//        modifier = Modifier.fillMaxWidth().padding(2.dp),
//        text = "test",
//        onChange = {},
//        onSend = {}
//    )
//}