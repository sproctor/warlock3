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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import cc.warlock.warlock3.app.WarlockIcons

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun MacrosView(
    macros: Map<String, String>,
    saveMacro: (String, String) -> Unit,
    deleteMacro: (String) -> Unit,
) {
    var editingMacro by remember { mutableStateOf<Pair<String, String>?>(null) }
    Column {
        Column(Modifier.fillMaxWidth().weight(1f)) {
            macros.forEach { macro ->
                ListItem(
                    modifier = Modifier.clickable { },
                    text = { Text(macro.key) },
                    secondaryText = { Text(macro.value) }
                )
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Button(onClick = { editingMacro = Pair("", "") }) {
                Icon(imageVector = WarlockIcons.Add, contentDescription = null)
            }
        }
    }
    editingMacro?.let { macro ->
        EditMacroDialog(
            key = macro.first,
            value = macro.second,
            saveMacro = { key, value ->
                if (key != macro.first) {
                    deleteMacro(macro.first)
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
    key: String,
    value: String,
    saveMacro: (String, String) -> Unit,
    onClose: () -> Unit,
) {
    Dialog(onCloseRequest = onClose) {
        Column {
            var selectedKey by remember { mutableStateOf<Key?>(null) }
            var modifierKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
            var newValue by remember(value) { mutableStateOf(value) }
            TextField(value = newValue, label = { Text("Value") }, onValueChange = { newValue = it })
            KeyboardLayout(
                selectedKey = selectedKey,
                modifierKeys = modifierKeys,
                onClickKey = { selectedKey = it },
                onClickModifier = {
                    modifierKeys = if (modifierKeys.contains(it)) modifierKeys + it else modifierKeys - it
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
        }
        Row {
            KeyButton(key = "`", isSelected = selectedKey == Key.Grave, onClick = { onClickKey(Key.Grave) })
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
