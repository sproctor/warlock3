package cc.warlock.warlock3.app.views.settings

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
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
    Dialog(onCloseRequest = onClose) {
        Column {
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
                width = 60.dp
            )
            KeyButton(key = "F1", isSelected = selectedKey == Key.F1, onClick = { onClickKey(Key.F1) })
            KeyButton(key = "F2", isSelected = selectedKey == Key.F2, onClick = { onClickKey(Key.F2) })
            KeyButton(key = "F3", isSelected = selectedKey == Key.F3, onClick = { onClickKey(Key.F3) })
            KeyButton(key = "F4", isSelected = selectedKey == Key.F4, onClick = { onClickKey(Key.F4) })
            KeyButton(key = "F5", isSelected = selectedKey == Key.F5, onClick = { onClickKey(Key.F5) })
            KeyButton(key = "F6", isSelected = selectedKey == Key.F6, onClick = { onClickKey(Key.F6) })
            KeyButton(key = "F7", isSelected = selectedKey == Key.F7, onClick = { onClickKey(Key.F7) })
            KeyButton(key = "F8", isSelected = selectedKey == Key.F8, onClick = { onClickKey(Key.F8) })
            KeyButton(key = "F9", isSelected = selectedKey == Key.F9, onClick = { onClickKey(Key.F9) })
            KeyButton(key = "F10", isSelected = selectedKey == Key.F10, onClick = { onClickKey(Key.F10) })
            KeyButton(key = "F11", isSelected = selectedKey == Key.F11, onClick = { onClickKey(Key.F11) })
            KeyButton(key = "F12", isSelected = selectedKey == Key.F12, onClick = { onClickKey(Key.F12) })
        }
        Row {
            KeyButton(key = "`", isSelected = selectedKey == Key.Grave, onClick = { onClickKey(Key.Grave) })
            KeyButton(key = "1", isSelected = selectedKey == Key.One, onClick = { onClickKey(Key.One) })
            KeyButton(key = "2", isSelected = selectedKey == Key.Two, onClick = { onClickKey(Key.Two) })
            KeyButton(key = "3", isSelected = selectedKey == Key.Three, onClick = { onClickKey(Key.Three) })
        }
        Row {
            KeyButton(
                key = "Tab",
                isSelected = selectedKey == Key.Tab,
                onClick = { onClickKey(Key.Tab) },
                width = 80.dp
            )

        }
        Row {
            KeyButton(key = "A", isSelected = selectedKey == Key.A, onClick = { onClickKey(Key.A) })
        }
        Row {
            KeyButton(
                key = "Shift",
                isSelected = modifierKeys.contains("shift"),
                onClick = { onClickModifier("shift") },
                width = 100.dp
            )
        }
        Row {
            KeyButton(
                key = "Ctrl",
                isSelected = modifierKeys.contains("ctrl"),
                onClick = { onClickModifier("ctrl") },
                width = 70.dp
            )
        }
    }
}

@Composable
fun KeyButton(
    key: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    width: Dp = 48.dp,
    modifier: Modifier = Modifier,
) {
    val backgroundColor = if (isSelected) MaterialTheme.colors.secondary else MaterialTheme.colors.surface
    val contentColor = MaterialTheme.colors.contentColorFor(backgroundColor)
    OutlinedButton(
        modifier = modifier.padding(4.dp).width(width).height(48.dp),
        onClick = onClick,
        colors = ButtonDefaults.outlinedButtonColors(backgroundColor = backgroundColor, contentColor = contentColor)
    ) {
        Text(text = key, style = MaterialTheme.typography.caption)
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