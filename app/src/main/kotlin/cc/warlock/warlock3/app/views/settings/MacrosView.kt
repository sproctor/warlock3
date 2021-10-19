package cc.warlock.warlock3.app.views.settings

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.*
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.WindowSize
import androidx.compose.ui.window.rememberDialogState
import cc.warlock.warlock3.app.WarlockIcons
import java.awt.event.KeyEvent

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun MacrosView(
    macros: Map<String, String>,
    saveMacro: (String, String) -> Unit,
    deleteMacro: (String) -> Unit,
) {
    var editingMacro by remember { mutableStateOf<Pair<String?, String>?>(null) }
    Column {
        Column(Modifier.fillMaxWidth().weight(1f)) {
            macros.forEach { macro ->
                val parts = macro.key.split("+")
                val textBuilder = StringBuilder()
                for (i in 0..(parts.size - 2)) {
                    textBuilder.append(parts[i])
                    textBuilder.append("+")
                }
                val key = Key(parts.last().toLongOrNull() ?: 0)
                textBuilder.append(KeyEvent.getKeyText(key.nativeKeyCode))
                ListItem(
                    modifier = Modifier.clickable { },
                    text = { Text(textBuilder.toString()) },
                    secondaryText = { Text(macro.value) }
                )
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Button(onClick = { editingMacro = Pair(null, "") }) {
                Icon(imageVector = WarlockIcons.Add, contentDescription = null)
            }
        }
    }
    editingMacro?.let { macro ->
        val (initialKey, modifiers) = macro.first?.let { stringToKey(it) } ?: Pair(null, emptySet())
        EditMacroDialog(
            key = initialKey,
            modifiers = modifiers,
            value = macro.second,
            saveMacro = { key, value ->
                if (key != macro.first) {
                    macro.first?.let { deleteMacro(it) }
                }
                saveMacro(key, value)
                editingMacro = null
            },
            onClose = { editingMacro = null }
        )
    }
}

@Composable
fun EditMacroDialog(
    key: Key?,
    modifiers: Set<String>,
    value: String,
    saveMacro: (String, String) -> Unit,
    onClose: () -> Unit,
) {
    Dialog(
        onCloseRequest = onClose,
        state = rememberDialogState(size = WindowSize(width = 1200.dp, height = 500.dp))
    ) {
        Column(
            modifier = Modifier
                .scrollable(
                    state = rememberScrollState(),
                    orientation = Orientation.Horizontal
                )
        ) {
            var selectedKey by remember { mutableStateOf<Key?>(key) }
            var modifierKeys by remember { mutableStateOf<Set<String>>(modifiers) }
            var newValue by remember(value) { mutableStateOf(value) }
            TextField(value = newValue, label = { Text("Value") }, onValueChange = { newValue = it })
            KeyboardLayout(
                selectedKey = selectedKey,
                modifierKeys = modifierKeys,
                onClickKey = { selectedKey = it },
                onClickModifier = {
                    modifierKeys = if (!modifierKeys.contains(it)) modifierKeys + it else modifierKeys - it
                },
            )
            Row {
                Button(
                    onClick = {
                        selectedKey?.let { key ->
                            val newKey = StringBuilder()
                            if (modifierKeys.contains("ctrl")) newKey.append("ctrl+")
                            if (modifierKeys.contains("alt")) newKey.append("alt+")
                            if (modifierKeys.contains("shift")) newKey.append("shift+")
                            newKey.append(key.keyCode.toString())
                            saveMacro(newKey.toString(), newValue)
                        }
                    }
                ) {
                    Text("OK")
                }
                Button(onClick = onClose) {
                    Text("CANCEL")
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun KeyboardLayout(
    selectedKey: Key?,
    modifierKeys: Set<String>,
    onClickKey: (Key) -> Unit,
    onClickModifier: (String) -> Unit,
) {
    Column {
        Row {
            KeyButton(
                key = "Esc",
                isSelected = selectedKey == Key.Escape,
                onClick = { onClickKey(Key.Escape) },
                width = 60.dp,
                modifier = Modifier.padding(end = 48.dp)
            )
            KeyButton(key = "F1", isSelected = selectedKey == Key.F1, onClick = { onClickKey(Key.F1) })
            KeyButton(key = "F2", isSelected = selectedKey == Key.F2, onClick = { onClickKey(Key.F2) })
            KeyButton(key = "F3", isSelected = selectedKey == Key.F3, onClick = { onClickKey(Key.F3) })
            KeyButton(
                key = "F4",
                isSelected = selectedKey == Key.F4,
                onClick = { onClickKey(Key.F4) },
                modifier = Modifier.padding(end = 20.dp)
            )
            KeyButton(key = "F5", isSelected = selectedKey == Key.F5, onClick = { onClickKey(Key.F5) })
            KeyButton(key = "F6", isSelected = selectedKey == Key.F6, onClick = { onClickKey(Key.F6) })
            KeyButton(key = "F7", isSelected = selectedKey == Key.F7, onClick = { onClickKey(Key.F7) })
            KeyButton(
                key = "F8",
                isSelected = selectedKey == Key.F8,
                onClick = { onClickKey(Key.F8) },
                modifier = Modifier.padding(end = 20.dp)
            )
            KeyButton(key = "F9", isSelected = selectedKey == Key.F9, onClick = { onClickKey(Key.F9) })
            KeyButton(key = "F10", isSelected = selectedKey == Key.F10, onClick = { onClickKey(Key.F10) })
            KeyButton(key = "F11", isSelected = selectedKey == Key.F11, onClick = { onClickKey(Key.F11) })
            KeyButton(
                key = "F12",
                isSelected = selectedKey == Key.F12,
                onClick = { onClickKey(Key.F12) },
                modifier = Modifier.padding(end = 8.dp)
            )
            KeyButton(
                key = "Prt Sc",
                isSelected = selectedKey == Key.PrintScreen,
                onClick = { onClickKey(Key.PrintScreen) })
            KeyButton(
                key = "Scr Lk",
                isSelected = selectedKey == Key.ScrollLock,
                onClick = { onClickKey(Key.ScrollLock) })
            KeyButton(key = "Brk", isSelected = selectedKey == Key.Break, onClick = { onClickKey(Key.Break) })
        }
        Row {
            KeyButton(key = "`", isSelected = selectedKey == Key.Grave, onClick = { onClickKey(Key.Grave) })
            KeyButton(key = "1", isSelected = selectedKey == Key.One, onClick = { onClickKey(Key.One) })
            KeyButton(key = "2", isSelected = selectedKey == Key.Two, onClick = { onClickKey(Key.Two) })
            KeyButton(key = "3", isSelected = selectedKey == Key.Three, onClick = { onClickKey(Key.Three) })
            KeyButton(key = "4", isSelected = selectedKey == Key.Four, onClick = { onClickKey(Key.Four) })
            KeyButton(key = "5", isSelected = selectedKey == Key.Five, onClick = { onClickKey(Key.Five) })
            KeyButton(key = "6", isSelected = selectedKey == Key.Six, onClick = { onClickKey(Key.Six) })
            KeyButton(key = "7", isSelected = selectedKey == Key.Seven, onClick = { onClickKey(Key.Seven) })
            KeyButton(key = "8", isSelected = selectedKey == Key.Eight, onClick = { onClickKey(Key.Eight) })
            KeyButton(key = "9", isSelected = selectedKey == Key.Nine, onClick = { onClickKey(Key.Nine) })
            KeyButton(key = "0", isSelected = selectedKey == Key.Zero, onClick = { onClickKey(Key.Zero) })
            KeyButton(key = "-", isSelected = selectedKey == Key.Minus, onClick = { onClickKey(Key.Minus) })
            KeyButton(key = "=", isSelected = selectedKey == Key.Equals, onClick = { onClickKey(Key.Equals) })
            KeyButton(
                key = "Bksp",
                isSelected = selectedKey == Key.Backspace,
                onClick = { onClickKey(Key.Backspace) },
                width = 80.dp,
                modifier = Modifier.padding(end = 8.dp)
            )

            KeyButton(key = "Ins", isSelected = selectedKey == Key.Insert, onClick = { onClickKey(Key.Insert) })
            KeyButton(key = "Hm", isSelected = selectedKey == Key.Home, onClick = { onClickKey(Key.Home) })
            KeyButton(
                key = "Pg Up",
                isSelected = selectedKey == Key.PageUp,
                onClick = { onClickKey(Key.PageUp) },
                modifier = Modifier.padding(end = 8.dp)
            )

            KeyButton(key = "Nm Lk", isSelected = selectedKey == Key.NumLock, onClick = { onClickKey(Key.NumLock) })
            KeyButton(
                key = "/",
                isSelected = selectedKey == Key.NumPadDivide,
                onClick = { onClickKey(Key.NumPadDivide) })
            KeyButton(
                key = "*",
                isSelected = selectedKey == Key.NumPadMultiply,
                onClick = { onClickKey(Key.NumPadMultiply) })
            KeyButton(
                key = "-",
                isSelected = selectedKey == Key.NumPadSubtract,
                onClick = { onClickKey(Key.NumPadSubtract) })
        }
        Row {
            KeyButton(
                key = "Tab",
                isSelected = selectedKey == Key.Tab,
                onClick = { onClickKey(Key.Tab) },
                width = 80.dp
            )
            KeyButton(key = "Q", isSelected = selectedKey == Key.Q, onClick = { onClickKey(Key.Q) })
            KeyButton(key = "W", isSelected = selectedKey == Key.W, onClick = { onClickKey(Key.W) })
            KeyButton(key = "E", isSelected = selectedKey == Key.E, onClick = { onClickKey(Key.E) })
            KeyButton(key = "R", isSelected = selectedKey == Key.R, onClick = { onClickKey(Key.R) })
            KeyButton(key = "T", isSelected = selectedKey == Key.T, onClick = { onClickKey(Key.T) })
            KeyButton(key = "Y", isSelected = selectedKey == Key.Y, onClick = { onClickKey(Key.Y) })
            KeyButton(key = "U", isSelected = selectedKey == Key.U, onClick = { onClickKey(Key.U) })
            KeyButton(key = "I", isSelected = selectedKey == Key.I, onClick = { onClickKey(Key.I) })
            KeyButton(key = "O", isSelected = selectedKey == Key.O, onClick = { onClickKey(Key.O) })
            KeyButton(key = "P", isSelected = selectedKey == Key.P, onClick = { onClickKey(Key.P) })
            KeyButton(key = "[", isSelected = selectedKey == Key.LeftBracket, onClick = { onClickKey(Key.LeftBracket) })
            KeyButton(
                key = "]",
                isSelected = selectedKey == Key.RightBracket,
                onClick = { onClickKey(Key.RightBracket) })
            KeyButton(
                key = "\\",
                isSelected = selectedKey == Key.Backslash,
                onClick = { onClickKey(Key.Backslash) },
                modifier = Modifier.padding(end = 8.dp)
            )

            KeyButton(key = "Del", isSelected = selectedKey == Key.Delete, onClick = { onClickKey(Key.Delete) })
            KeyButton(key = "End", isSelected = selectedKey == Key.MoveEnd, onClick = { onClickKey(Key.MoveEnd) })
            KeyButton(
                key = "Pg Dn",
                isSelected = selectedKey == Key.PageDown,
                onClick = { onClickKey(Key.PageDown) },
                modifier = Modifier.padding(end = 8.dp)
            )

            KeyButton(key = "7", isSelected = selectedKey == Key.NumPad7, onClick = { onClickKey(Key.NumPad7) })
            KeyButton(key = "8", isSelected = selectedKey == Key.NumPad8, onClick = { onClickKey(Key.NumPad8) })
            KeyButton(key = "9", isSelected = selectedKey == Key.NumPad9, onClick = { onClickKey(Key.NumPad9) })
            KeyButton(key = "+", isSelected = selectedKey == Key.NumPadAdd, onClick = { onClickKey(Key.NumPadAdd) })
        }
        Row {
            KeyButton(key = "Caps Lock", enabled = false, width = 100.dp)
            KeyButton(key = "A", isSelected = selectedKey == Key.A, onClick = { onClickKey(Key.A) })
            KeyButton(key = "S", isSelected = selectedKey == Key.S, onClick = { onClickKey(Key.S) })
            KeyButton(key = "D", isSelected = selectedKey == Key.D, onClick = { onClickKey(Key.D) })
            KeyButton(key = "F", isSelected = selectedKey == Key.F, onClick = { onClickKey(Key.F) })
            KeyButton(key = "G", isSelected = selectedKey == Key.G, onClick = { onClickKey(Key.G) })
            KeyButton(key = "H", isSelected = selectedKey == Key.H, onClick = { onClickKey(Key.H) })
            KeyButton(key = "J", isSelected = selectedKey == Key.J, onClick = { onClickKey(Key.J) })
            KeyButton(key = "K", isSelected = selectedKey == Key.K, onClick = { onClickKey(Key.K) })
            KeyButton(key = "L", isSelected = selectedKey == Key.L, onClick = { onClickKey(Key.L) })
            KeyButton(key = ";", isSelected = selectedKey == Key.Semicolon, onClick = { onClickKey(Key.Semicolon) })
            KeyButton(key = "'", isSelected = selectedKey == Key.Apostrophe, onClick = { onClickKey(Key.Apostrophe) })
            KeyButton(
                key = "Enter",
                isSelected = selectedKey == Key.Enter,
                onClick = { onClickKey(Key.Enter) },
                width = 108.dp,
                modifier = Modifier.padding(end = 220.dp)
            )
            KeyButton(key = "4", isSelected = selectedKey == Key.NumPad4, onClick = { onClickKey(Key.NumPad4) })
            KeyButton(key = "5", isSelected = selectedKey == Key.NumPad5, onClick = { onClickKey(Key.NumPad5) })
            KeyButton(key = "6", isSelected = selectedKey == Key.NumPad6, onClick = { onClickKey(Key.NumPad6) })
        }
        Row {
            KeyButton(
                key = "Shift",
                isSelected = modifierKeys.contains("shift"),
                onClick = { onClickModifier("shift") },
                width = 138.dp
            )
            KeyButton(key = "Z", isSelected = selectedKey == Key.Z, onClick = { onClickKey(Key.Z) })
            KeyButton(key = "X", isSelected = selectedKey == Key.X, onClick = { onClickKey(Key.X) })
            KeyButton(key = "C", isSelected = selectedKey == Key.C, onClick = { onClickKey(Key.C) })
            KeyButton(key = "V", isSelected = selectedKey == Key.V, onClick = { onClickKey(Key.V) })
            KeyButton(key = "B", isSelected = selectedKey == Key.B, onClick = { onClickKey(Key.B) })
            KeyButton(key = "N", isSelected = selectedKey == Key.N, onClick = { onClickKey(Key.N) })
            KeyButton(key = "M", isSelected = selectedKey == Key.M, onClick = { onClickKey(Key.M) })
            KeyButton(key = ",", isSelected = selectedKey == Key.Comma, onClick = { onClickKey(Key.Comma) })
            KeyButton(key = ".", isSelected = selectedKey == Key.Period, onClick = { onClickKey(Key.Period) })
            KeyButton(key = "/", isSelected = selectedKey == Key.Slash, onClick = { onClickKey(Key.Slash) })
            KeyButton(
                key = "Shift",
                isSelected = modifierKeys.contains("shift"),
                onClick = { onClickModifier("shift") },
                width = 138.dp,
                modifier = Modifier.padding(end = 76.dp)
            )

            KeyButton(
                icon = WarlockIcons.KeyboardArrowUp,
                isSelected = selectedKey == Key.DirectionUp,
                onClick = { onClickKey(Key.DirectionUp) },
                modifier = Modifier.padding(end = 76.dp)
            )

            KeyButton(key = "1", isSelected = selectedKey == Key.NumPad1, onClick = { onClickKey(Key.NumPad1) })
            KeyButton(key = "2", isSelected = selectedKey == Key.NumPad2, onClick = { onClickKey(Key.NumPad2) })
            KeyButton(key = "3", isSelected = selectedKey == Key.NumPad3, onClick = { onClickKey(Key.NumPad3) })
            KeyButton(
                key = "Ent",
                isSelected = selectedKey == Key.NumPadEnter,
                onClick = { onClickKey(Key.NumPadEnter) })
        }
        Row {
            KeyButton(
                key = "Ctrl",
                isSelected = modifierKeys.contains("ctrl"),
                onClick = { onClickModifier("ctrl") },
                width = 100.dp,
                modifier = Modifier.padding(end = 56.dp)
            )
            KeyButton(
                key = "Alt",
                isSelected = modifierKeys.contains("alt"),
                onClick = { onClickModifier("alt") },
                width = 100.dp
            )
            KeyButton(
                key = "Space",
                isSelected = selectedKey == Key.Spacebar,
                onClick = { onClickKey(Key.Spacebar) },
                width = 420.dp
            )
            KeyButton(
                key = "Alt",
                isSelected = modifierKeys.contains("alt"),
                onClick = { onClickModifier("alt") },
                width = 100.dp,
                modifier = Modifier.padding(end = 56.dp)
            )
            KeyButton(
                key = "Ctrl",
                isSelected = modifierKeys.contains("ctrl"),
                onClick = { onClickModifier("ctrl") },
                width = 100.dp,
                modifier = Modifier.padding(end = 8.dp)
            )

            KeyButton(
                icon = WarlockIcons.KeyboardArrowLeft,
                isSelected = selectedKey == Key.DirectionLeft,
                onClick = { onClickKey(Key.DirectionLeft) })
            KeyButton(
                icon = WarlockIcons.KeyboardArrowDown,
                isSelected = selectedKey == Key.DirectionDown,
                onClick = { onClickKey(Key.DirectionDown) })
            KeyButton(
                icon = WarlockIcons.KeyboardArrowRight,
                isSelected = selectedKey == Key.DirectionRight,
                onClick = { onClickKey(Key.DirectionRight) },
                modifier = Modifier.padding(end = 8.dp)
            )

            KeyButton(
                key = "0",
                isSelected = selectedKey == Key.NumPad0,
                onClick = { onClickKey(Key.NumPad0) },
                width = 128.dp
            )
            KeyButton(
                key = ".",
                isSelected = selectedKey == Key.NumPadDot,
                onClick = { onClickKey(Key.NumPadDot) },
            )
        }
    }
}

@Composable
fun KeyButton(
    key: String? = null,
    icon: ImageVector? = null,
    isSelected: Boolean = false,
    onClick: () -> Unit = {},
    width: Dp = 60.dp,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val backgroundColor = if (isSelected) MaterialTheme.colors.secondary else MaterialTheme.colors.surface
    val contentColor = MaterialTheme.colors.contentColorFor(backgroundColor)
    OutlinedButton(
        modifier = modifier.padding(4.dp).width(width).height(48.dp),
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.outlinedButtonColors(backgroundColor = backgroundColor, contentColor = contentColor)
    ) {
        key?.let { Text(text = it, style = MaterialTheme.typography.body2) }
        icon?.let { Icon(imageVector = it, contentDescription = null, modifier = Modifier.size(48.dp)) }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Preview
@Composable
fun KeyboardLayoutPreview() {
    KeyboardLayout(
        selectedKey = Key.A,
        modifierKeys = emptySet(),
        onClickKey = {},
        onClickModifier = {},
    )
}

fun stringToKey(value: String): Pair<Key, Set<String>> {
    val modifiers = mutableSetOf<String>()
    val parts = value.split("+")
    for (i in 0..(parts.size - 2)) {
        modifiers.add(parts[i])
    }
    val key = Key(parts.last().toLongOrNull() ?: 0)
    return key to modifiers
}