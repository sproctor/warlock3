package cc.warlock.warlock3.app.view

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.warlock.warlock3.app.viewmodel.GameViewModel
import kotlin.math.min

@Composable
fun WarlockEntry(modifier: Modifier, viewModel: GameViewModel) {
    val history by viewModel.sendHistory
    val roundTime by viewModel.roundTime.collectAsState()
    val castTime by viewModel.castTime.collectAsState()

    WarlockEntryContent(
        modifier = modifier,
        onSend = {
            viewModel.send(it)
        },
        stopScripts = { viewModel.stopScripts() },
        history = history,
        roundTime = roundTime,
        castTime = castTime,
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun WarlockEntryContent(
    modifier: Modifier,
    onSend: (String) -> Unit,
    stopScripts: () -> Unit,
    history: List<String>,
    roundTime: Int,
    castTime: Int,
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
                        event.key.keyCode == Key.Escape.keyCode && event.type == KeyEventType.KeyDown -> {
                            stopScripts()
                            true
                        }
                        else -> false
                    }
                },
            textStyle = TextStyle.Default.copy(fontSize = 16.sp),
            onValueChange = { textField = it },
            maxLines = 1,
        )

        RoundTimeBar(roundTime, castTime)

        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
            focusRequester.captureFocus()
        }
    }
}

@Composable
fun BoxScope.RoundTimeBar(
    roundTime: Int,
    castTime: Int,
) {
    val rtColor = Color(139, 0, 0, 0xC0)
    val stColor = Color(0, 0, 139, 0xC0)
    Canvas(Modifier.matchParentSize().padding(2.dp)) {
        for (i in castTime until min(100, roundTime)) {
            drawRect(
                color = rtColor,
                topLeft = Offset(x = i * 16f, y = 0f),
                size = Size(width = 12f, height = size.height)
            )
        }
        for (i in roundTime until min(100, castTime)) {
            drawRect(
                color = stColor,
                topLeft = Offset(x = i * 16f, y = 0f),
                size = Size(width = 12f, height = size.height)
            )
        }
        for (i in 0 until min(min(castTime, roundTime), 100)) {
            drawRect(
                color = rtColor,
                topLeft = Offset(x = i * 16f, y = 0f),
                size = Size(width = 12f, height = size.height / 2f)
            )
            drawRect(
                color = stColor,
                topLeft = Offset(x = i * 16f, y = size.height / 2f),
                size = Size(width = 12f, height = size.height / 2f)
            )
        }
    }
}

@Preview
@Composable
fun WarlockEntryPreview() {
    WarlockEntryContent(
        modifier = Modifier.fillMaxWidth().padding(2.dp),
        onSend = {},
        history = emptyList(),
        stopScripts = {},
        roundTime = 8,
        castTime = 4,
    )
}

@Preview
@Composable
fun RoundTimeBarPreview() {
    Box(Modifier.width(800.dp).height(50.dp).background(Color.DarkGray)) {
        RoundTimeBar(8, 0)
    }
}
