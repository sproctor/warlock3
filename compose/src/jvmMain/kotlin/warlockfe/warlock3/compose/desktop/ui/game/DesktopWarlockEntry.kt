package warlockfe.warlock3.compose.desktop.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.contextmenu.modifier.appendTextContextMenuComponents
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import warlockfe.warlock3.compose.desktop.ui.settings.DesktopWindowSettingsDialog
import warlockfe.warlock3.compose.ui.game.GameViewModel
import warlockfe.warlock3.compose.util.SettingsContextMenuItemKey
import warlockfe.warlock3.compose.util.addItem
import warlockfe.warlock3.compose.util.createFontFamily
import warlockfe.warlock3.compose.util.toColor
import warlockfe.warlock3.core.prefs.repositories.defaultStyles
import warlockfe.warlock3.core.text.StyleDefinition
import kotlin.math.min
import kotlin.time.Duration
import kotlin.time.Instant

@Suppress("ktlint:compose:vm-forwarding-check")
@Composable
fun DesktopWarlockEntry(
    viewModel: GameViewModel,
    entryFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val roundTime =
        countdownSeconds(
            endTime = viewModel.roundTimeEnd.collectAsState().value,
            getCurrentTime = viewModel::getCurrentTime,
        )
    val castTime =
        countdownSeconds(
            endTime = viewModel.castTimeEnd.collectAsState().value,
            getCurrentTime = viewModel::getCurrentTime,
        )
    val presets by viewModel.presets.collectAsState(emptyMap())
    val defaultStyle = presets["default"] ?: defaultStyles["default"]!!
    val style = presets["entry"] ?: StyleDefinition()
    DesktopWarlockEntryContent(
        style = style,
        defaultStyle = defaultStyle,
        state = viewModel.entryTextState,
        entryFocusRequester = entryFocusRequester,
        roundTime = roundTime,
        castTime = castTime,
        sendCommand = viewModel::submit,
        saveStyle = viewModel::saveEntryStyle,
        modifier = modifier,
    )
}

@Composable
fun DesktopWarlockEntryContent(
    style: StyleDefinition,
    defaultStyle: StyleDefinition,
    state: TextFieldState,
    entryFocusRequester: FocusRequester,
    sendCommand: () -> Unit,
    roundTime: Int,
    castTime: Int,
    saveStyle: (StyleDefinition) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSettingsDialog by remember { mutableStateOf(false) }
    val usableStyle = style.mergeWith(defaultStyle)
    val backgroundColor = usableStyle.backgroundColor.toColor()
    val frameShape = RoundedCornerShape(2.dp)
    Box(
        modifier =
            modifier
                .background(color = backgroundColor, shape = frameShape)
                .border(
                    width = Dp.Hairline,
                    color = JewelTheme.globalColors.borders.normal,
                    shape = frameShape,
                ),
    ) {
        Box(
            modifier = Modifier.padding(3.dp),
        ) {
            DesktopRoundTimeBar(backgroundColor, roundTime, castTime)

            val defaultTextStyle = JewelTheme.defaultTextStyle
            val textStyle =
                remember(usableStyle) {
                    val fontSize = (usableStyle.fontSize ?: 16f).sp
                    defaultTextStyle.copy(
                        fontSize = fontSize,
                        fontFamily =
                            usableStyle.fontFamily?.let { createFontFamily(it) }
                                ?: defaultTextStyle.fontFamily,
                        lineHeight = fontSize,
                        color = usableStyle.textColor.toColor(),
                    )
                }
            BasicTextField(
                state = state,
                modifier =
                    Modifier
                        .padding(5.dp)
                        .align(Alignment.CenterStart)
                        .focusRequester(entryFocusRequester)
                        .fillMaxWidth()
                        .appendTextContextMenuComponents {
                            separator()
                            addItem(key = SettingsContextMenuItemKey, label = "Settings") {
                                showSettingsDialog = true
                                close()
                            }
                        },
                textStyle = textStyle,
                cursorBrush = SolidColor(usableStyle.textColor.toColor()),
                lineLimits = TextFieldLineLimits.SingleLine,
                keyboardOptions =
                    KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Send,
                    ),
                onKeyboardAction = {
                    sendCommand()
                },
            )

            LaunchedEffect(Unit) {
                entryFocusRequester.requestFocus()
            }
        }
    }
    if (showSettingsDialog) {
        DesktopWindowSettingsDialog(
            onCloseRequest = { showSettingsDialog = false },
            style = style,
            defaultStyle = defaultStyle,
            saveStyle = saveStyle,
        )
    }
}

@Composable
fun BoxScope.DesktopRoundTimeBar(
    backgroundColor: Color,
    roundTime: Int,
    castTime: Int,
    modifier: Modifier = Modifier,
) {
    val darkMode = backgroundColor.luminance() < 0.5f
    val rtColor =
        if (darkMode) {
            Color(0xff, 0x50, 0x50)
        } else {
            Color(0xe0, 0x3c, 0x31)
        }
    val ctColor =
        if (darkMode) {
            Color(0x60, 0x80, 0xff)
        } else {
            Color(0x30, 0x30, 0xff)
        }
    Canvas(modifier.matchParentSize().padding(horizontal = 5.dp).clipToBounds()) {
        val segmentSize = Size(width = 15.dp.toPx(), height = 4.dp.toPx())
        val segmentSpacing = 5.dp.toPx()
        for (i in 0 until min(100, roundTime)) {
            drawRect(
                color = rtColor,
                topLeft =
                    Offset(
                        x = i * (segmentSize.width + segmentSpacing),
                        y = size.height - segmentSize.height,
                    ),
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

@Composable
private fun countdownSeconds(
    endTime: Instant?,
    getCurrentTime: () -> Instant,
): Int {
    var seconds by remember { mutableIntStateOf(0) }
    val currentGetCurrentTime by rememberUpdatedState(getCurrentTime)
    LaunchedEffect(endTime) {
        while (endTime != null) {
            val now = currentGetCurrentTime()
            val remaining = endTime - now
            val remainingMs = remaining.inWholeMilliseconds
            seconds = ((remainingMs + 999) / 1000).toInt()
            if (remaining < Duration.ZERO) break
            val msUntilNextTick = remainingMs % 1000
            delay(if (msUntilNextTick == 0L) 1000L else msUntilNextTick)
        }
    }
    return seconds
}
