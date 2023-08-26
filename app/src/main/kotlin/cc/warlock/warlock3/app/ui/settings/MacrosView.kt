package cc.warlock.warlock3.app.ui.settings

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import cc.warlock.warlock3.app.ui.theme.WarlockIcons
import cc.warlock.warlock3.core.client.GameCharacter
import cc.warlock.warlock3.core.prefs.MacroRepository
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.awt.event.KeyEvent

@OptIn(DelicateCoroutinesApi::class)
@Composable
fun MacrosView(
    initialCharacter: GameCharacter?,
    characters: List<GameCharacter>,
    macroRepository: MacroRepository,
) {
    var currentCharacter by remember(initialCharacter) { mutableStateOf(initialCharacter) }
    val macros by if (currentCharacter == null) {
        macroRepository.observeGlobalMacros()
    } else {
        macroRepository.observeOnlyCharacterMacros(currentCharacter!!.id)
    }.collectAsState(emptyList())
    var editingMacro by remember { mutableStateOf<Pair<String?, String>?>(null) }
    val scope = rememberCoroutineScope()

    Column {
        SettingsCharacterSelector(
            selectedCharacter = currentCharacter,
            characters = characters,
            onSelect = { currentCharacter = it },
            allowGlobal = true,
        )
        Spacer(Modifier.height(16.dp))
        Text(text = "Macros", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        val scrollState = rememberScrollState()
        Box(
            Modifier.fillMaxWidth()
                .weight(1f)
        ) {
            Column(
                Modifier.fillMaxSize()
                    .padding(end = LocalScrollbarStyle.current.thickness)
                    .verticalScroll(scrollState)
            ) {
                macros.forEach { macro ->
                    val parts = macro.first.split("+")
                    val textBuilder = StringBuilder()
                    for (i in 0..(parts.size - 2)) {
                        textBuilder.append(parts[i])
                        textBuilder.append("+")
                    }
                    val key = Key(parts.last().toLongOrNull() ?: 0)
                    textBuilder.append(KeyEvent.getKeyText(key.nativeKeyCode))
                    ListItem(
                        headlineContent = { Text(textBuilder.toString()) },
                        supportingContent = { Text(macro.second) },
                        trailingContent = {
                            Row {
                                IconButton(
                                    onClick = { editingMacro = macro}
                                ) {
                                    Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit")
                                }
                                Spacer(Modifier.width(8.dp))
                                IconButton(
                                    onClick = {
                                        GlobalScope.launch {
                                            macroRepository.delete(currentCharacter?.id ?: "global", macro.first)
                                        }
                                    }
                                ) {
                                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete")
                                }
                            }
                        }
                    )
                }
            }
            VerticalScrollbar(
                modifier = Modifier.align(Alignment.CenterEnd)
                    .fillMaxHeight(),
                adapter = rememberScrollbarAdapter(scrollState)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(onClick = { editingMacro = Pair(null, "") }) {
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
                scope.launch {
                    if (key != macro.first) {
                        macro.first?.let {
                            if (currentCharacter != null) {
                                macroRepository.delete(currentCharacter!!.id, it)
                            } else {
                                macroRepository.deleteGlobal(it)
                            }
                        }
                    }
                    if (currentCharacter != null) {
                        macroRepository.put(currentCharacter!!.id, key, value)
                    } else {
                        macroRepository.putGlobal(key, value)
                    }
                    editingMacro = null
                }
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
    DialogWindow(
        onCloseRequest = onClose,
        state = rememberDialogState(size = DpSize(width = 1520.dp, height = 540.dp)),
        title = "Edit Macro"
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .scrollable(
                    state = rememberScrollState(),
                    orientation = Orientation.Horizontal
                )
        ) {
            var selectedKey by remember { mutableStateOf(key) }
            var modifierKeys by remember { mutableStateOf(modifiers) }
            var newValue by remember(value) { mutableStateOf(value) }
            TextField(value = newValue, label = { Text("Value") }, onValueChange = { newValue = it })
            Spacer(Modifier.height(16.dp))
            KeyboardLayout(
                selectedKey = selectedKey,
                modifierKeys = modifierKeys,
                onClickKey = { selectedKey = it },
                onClickModifier = {
                    modifierKeys = if (!modifierKeys.contains(it)) modifierKeys + it else modifierKeys - it
                },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(onClick = onClose) {
                    Text("CANCEL")
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    enabled = selectedKey != null,
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

            val numpadHome = Key(KeyEvent.VK_HOME, KeyEvent.KEY_LOCATION_NUMPAD)
            KeyButton(key = "7", isSelected = selectedKey == numpadHome, onClick = { onClickKey(numpadHome) })
            val numpadUp = Key(KeyEvent.VK_KP_UP, KeyEvent.KEY_LOCATION_NUMPAD)
            KeyButton(key = "8", isSelected = selectedKey == numpadUp, onClick = { onClickKey(numpadUp) })
            val numpadPgUp = Key(KeyEvent.VK_PAGE_UP, KeyEvent.KEY_LOCATION_NUMPAD)
            KeyButton(key = "9", isSelected = selectedKey == numpadPgUp, onClick = { onClickKey(numpadPgUp) })
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
            val numpadLeft = Key(KeyEvent.VK_KP_LEFT, KeyEvent.KEY_LOCATION_NUMPAD)
            KeyButton(key = "4", isSelected = selectedKey == numpadLeft, onClick = { onClickKey(numpadLeft) })
            val numpadCenter = Key(KeyEvent.VK_BEGIN, KeyEvent.KEY_LOCATION_NUMPAD)
            KeyButton(key = "5", isSelected = selectedKey == numpadCenter, onClick = { onClickKey(numpadCenter) })
            val numpadRight = Key(KeyEvent.VK_KP_RIGHT, KeyEvent.KEY_LOCATION_NUMPAD)
            KeyButton(key = "6", isSelected = selectedKey == numpadRight, onClick = { onClickKey(numpadRight) })
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

            val numpadEnd = Key(KeyEvent.VK_END, KeyEvent.KEY_LOCATION_NUMPAD)
            KeyButton(key = "1", isSelected = selectedKey == numpadEnd, onClick = { onClickKey(numpadEnd) })
            val numpadDown = Key(KeyEvent.VK_KP_DOWN, KeyEvent.KEY_LOCATION_NUMPAD)
            KeyButton(key = "2", isSelected = selectedKey == numpadDown, onClick = { onClickKey(numpadDown) })
            val numpadPgDn = Key(KeyEvent.VK_PAGE_DOWN, KeyEvent.KEY_LOCATION_NUMPAD)
            KeyButton(key = "3", isSelected = selectedKey == numpadPgDn, onClick = { onClickKey(numpadPgDn) })
            val numpadEnter = Key(KeyEvent.VK_ENTER, KeyEvent.KEY_LOCATION_NUMPAD)
            KeyButton(
                key = "Ent",
                isSelected = selectedKey == numpadEnter,
                onClick = { onClickKey(numpadEnter) })
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

            val numpadIns = Key(KeyEvent.VK_INSERT, KeyEvent.KEY_LOCATION_NUMPAD)
            KeyButton(
                key = "0",
                isSelected = selectedKey == numpadIns,
                onClick = { onClickKey(numpadIns) },
                width = 128.dp
            )
            val numpadDel = Key(KeyEvent.VK_DELETE, KeyEvent.KEY_LOCATION_NUMPAD)
            KeyButton(
                key = ".",
                isSelected = selectedKey == numpadDel,
                onClick = { onClickKey(numpadDel) },
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
    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surface
    val contentColor = MaterialTheme.colorScheme.contentColorFor(backgroundColor)
    OutlinedButton(
        modifier = modifier.padding(4.dp).width(width).height(48.dp),
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.outlinedButtonColors(containerColor = backgroundColor, contentColor = contentColor)
    ) {
        key?.let { Text(text = it, style = MaterialTheme.typography.bodyMedium) }
        icon?.let { Icon(imageVector = it, contentDescription = null, modifier = Modifier.size(48.dp)) }
    }
}

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