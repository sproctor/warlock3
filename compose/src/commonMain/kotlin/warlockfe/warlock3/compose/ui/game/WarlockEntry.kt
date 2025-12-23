package warlockfe.warlock3.compose.ui.game

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.contextmenu.modifier.appendTextContextMenuComponents
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import warlockfe.warlock3.compose.ui.settings.WindowSettingsDialog
import warlockfe.warlock3.compose.util.addItem
import warlockfe.warlock3.compose.util.createFontFamily
import warlockfe.warlock3.compose.util.toColor
import warlockfe.warlock3.core.prefs.repositories.defaultStyles
import warlockfe.warlock3.core.text.StyleDefinition
import kotlin.math.min

@Composable
fun WarlockEntry(
    viewModel: GameViewModel,
    entryFocusRequester: FocusRequester,
) {
    val roundTime by viewModel.roundTime.collectAsState()
    val castTime by viewModel.castTime.collectAsState()
    val presets by viewModel.presets.collectAsState(emptyMap())
    val defaultStyle = presets["default"] ?: defaultStyles["default"]!!
    val style = presets["entry"] ?: StyleDefinition()
    WarlockEntryContent(
        style = style,
        defaultStyle = defaultStyle,
        state = viewModel.entryText,
        entryFocusRequester = entryFocusRequester,
        roundTime = roundTime,
        castTime = castTime,
        sendCommand = viewModel::submit,
        saveStyle = viewModel::saveEntryStyle,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WarlockEntryContent(
    style: StyleDefinition,
    defaultStyle: StyleDefinition,
    state: TextFieldState,
    entryFocusRequester: FocusRequester,
    sendCommand: () -> Unit,
    roundTime: Int,
    castTime: Int,
    saveStyle: (StyleDefinition) -> Unit,
) {
    var showSettingsDialog by remember { mutableStateOf(false) }
    val usableStyle = style.mergeWith(defaultStyle)
    val backgroundColor = usableStyle.backgroundColor.toColor()
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        border = BorderStroke(Dp.Hairline, MaterialTheme.colorScheme.outline),
        color = backgroundColor,
    ) {
        Box(
            modifier = Modifier.padding(3.dp)
        ) {
            RoundTimeBar(backgroundColor, roundTime, castTime)

            val defaultTextStyle = LocalTextStyle.current
            val textStyle = remember(usableStyle) {
                val fontSize = (usableStyle.fontSize ?: 16f).sp
                defaultTextStyle.copy(
                    fontSize = fontSize,
                    fontFamily = usableStyle.fontFamily?.let { createFontFamily(it) } ?: defaultTextStyle.fontFamily,
                    lineHeight = fontSize,
                    color = usableStyle.textColor.toColor()
                )
            }
            BasicTextField(
                state = state,
                modifier = Modifier
                    .padding(5.dp)
                    .align(Alignment.CenterStart)
                    .focusRequester(entryFocusRequester)
                    .fillMaxWidth()
                    .appendTextContextMenuComponents {
                        separator()
                        addItem(key = style, label = "Settings") {
                            showSettingsDialog = true
                            close()
                        }
                    },
                textStyle = textStyle,
                cursorBrush = SolidColor(usableStyle.textColor.toColor()),
                lineLimits = TextFieldLineLimits.SingleLine,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Send,
                ),
                onKeyboardAction = {
                    sendCommand()
                }
            )

            LaunchedEffect(Unit) {
                entryFocusRequester.requestFocus()
            }
        }
    }
    if (showSettingsDialog) {
        WindowSettingsDialog(
            onCloseRequest = { showSettingsDialog = false },
            style = style,
            defaultStyle = defaultStyle,
            saveStyle = saveStyle,
        )
    }
}

@Composable
fun BoxScope.RoundTimeBar(
    backgroundColor: Color,
    roundTime: Int,
    castTime: Int,
) {
    val darkMode = backgroundColor.luminance() < 0.5f
    // TODO: get these colors from the skin
    val rtColor = if (darkMode) {
        Color(0xff, 0x50, 0x50)
    } else {
        Color(0xe0, 0x3c, 0x31)
    }
    val ctColor = if (darkMode) {
        Color(0x60, 0x80, 0xff)
    } else {
        Color(0x30, 0x30, 0xff)
    }
    Canvas(Modifier.matchParentSize().padding(horizontal = 5.dp).clipToBounds()) {
        val segmentSize = Size(width = 15.dp.toPx(), height = 4.dp.toPx())
        val segmentSpacing = 5.dp.toPx()
        for (i in 0 until min(100, roundTime)) {
            drawRect(
                color = rtColor,
                topLeft = Offset(x = i * (segmentSize.width + segmentSpacing), y = size.height - segmentSize.height),
                size = segmentSize,
            )
        }
        for (i in 0 until min(100, castTime)) {
            drawRect(
                color = ctColor,
                topLeft = Offset(x = i * (segmentSize.width + segmentSpacing), y = 0f),
                size = segmentSize,
            )
        }
    }
    Row(Modifier.align(Alignment.CenterEnd)) {
        if (castTime > 0) {
            Text(
                text = castTime.toString(),
                color = ctColor,
                fontWeight = FontWeight.Bold,
            )
        }
        if (roundTime > 0) {
            Spacer(Modifier.width(12.dp))
            Text(
                text = roundTime.toString(),
                color = rtColor,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Preview(widthDp = 800, backgroundColor = 0xFF444444)
@Composable
fun WarlockEntryDarkPreview() {
    WarlockEntryContent(
        state = rememberTextFieldState("test"),
        roundTime = 8,
        castTime = 4,
        entryFocusRequester = remember { FocusRequester() },
        style = defaultStyles["default"]!!,
        defaultStyle = defaultStyles["default"]!!,
        saveStyle = {},
        sendCommand = {},
    )
}

@Preview(widthDp = 800, backgroundColor = 0xFFFFFFFF)
@Composable
fun WarlockEntryLightPreview() {
    WarlockEntryContent(
        state = rememberTextFieldState("test"),
        roundTime = 8,
        castTime = 4,
        entryFocusRequester = remember { FocusRequester() },
        style = defaultStyles["default"]!!,
        defaultStyle = defaultStyles["default"]!!,
        saveStyle = {},
        sendCommand = {},
    )
}

@Preview(widthDp = 800, heightDp = 50, backgroundColor = 0xFF444444)
@Composable
fun RoundTimeBarPreview() {
    val backgroundColor = Color(0xFF444444)
    Box(Modifier.fillMaxSize().background(backgroundColor).padding(2.dp)) {
        RoundTimeBar(Color(0xFF444444), 8, 6)
    }
}

@Preview(widthDp = 800, heightDp = 50, backgroundColor = 0xFFFFFFFF)
@Composable
fun RoundTimeBarLightPreview() {
    Box(Modifier.fillMaxSize().background(Color.White).padding(2.dp)) {
        RoundTimeBar(Color.White, 8, 6)
    }
}
