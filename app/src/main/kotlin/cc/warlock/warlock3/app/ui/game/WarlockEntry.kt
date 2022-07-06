package cc.warlock.warlock3.app.ui.game

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.min

@Composable
fun WarlockEntry(modifier: Modifier, viewModel: GameViewModel) {
    val roundTime by viewModel.roundTime.collectAsState()
    val castTime by viewModel.castTime.collectAsState()

    WarlockEntryContent(
        modifier = modifier,
        textField = viewModel.entryText.value,
        onValueChange = viewModel::setEntryText,
        onKeyPress = viewModel::handleKeyPress,
        roundTime = roundTime,
        castTime = castTime,
    )
}

@Composable
fun WarlockEntryContent(
    modifier: Modifier,
    textField: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onKeyPress: (KeyEvent) -> Boolean,
    roundTime: Int,
    castTime: Int,
) {
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
                    val result = onKeyPress(event)
                    result
                },
            textStyle = TextStyle.Default.copy(fontSize = 16.sp),
            onValueChange = onValueChange,
            maxLines = 1,
        )

        RoundTimeBar(roundTime, castTime)

        DisposableEffect(Unit) {
            focusRequester.requestFocus()
            focusRequester.captureFocus()
            onDispose {
                focusRequester.freeFocus()
            }
        }
    }
}

@Composable
fun BoxScope.RoundTimeBar(
    roundTime: Int,
    castTime: Int,
) {
    val rtColor = Color(139, 0, 0, 0x80)
    val stColor = Color(0, 0, 139, 0x80)
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
        textField = TextFieldValue("test"),
        roundTime = 8,
        castTime = 4,
        onKeyPress = { true },
        onValueChange = {},
    )
}

@Preview
@Composable
fun RoundTimeBarPreview() {
    Box(Modifier.width(800.dp).height(50.dp).background(Color.DarkGray)) {
        RoundTimeBar(8, 0)
    }
}
