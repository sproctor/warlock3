package warlockfe.warlock3.compose.util

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import warlockfe.warlock3.core.macro.MacroKeyCombo

// Shared helpers for translating between Compose key events, the macro key-combo model, and the
// "ctrl+alt+shift+meta+key" display string. Used by both the desktop (Jewel) and mobile (Material3)
// macro editors, which otherwise duplicated all of this.

internal fun buildKeyString(
    key: Key?,
    modifierKeys: Set<String>,
): String {
    val newKey = StringBuilder()
    if (modifierKeys.contains("ctrl")) newKey.append("ctrl+")
    if (modifierKeys.contains("alt")) newKey.append("alt+")
    if (modifierKeys.contains("shift")) newKey.append("shift+")
    if (modifierKeys.contains("meta")) newKey.append("meta+")
    if (key != null) newKey.append(key.getLabel())
    return newKey.toString()
}

internal fun KeyEvent.getKeyModifiers(): Set<String> {
    val modifiers = mutableSetOf<String>()
    if (isAltPressed) modifiers.add("alt")
    if (isShiftPressed) modifiers.add("shift")
    if (isMetaPressed) modifiers.add("meta")
    if (isCtrlPressed) modifiers.add("ctrl")
    return modifiers
}

internal fun buildKeyCombo(
    key: Key,
    modifierKeys: Set<String>,
): MacroKeyCombo =
    MacroKeyCombo(
        keyCode = key.keyCode,
        ctrl = modifierKeys.contains("ctrl"),
        alt = modifierKeys.contains("alt"),
        shift = modifierKeys.contains("shift"),
        meta = modifierKeys.contains("meta"),
    )

internal fun keyComboToKey(keyCombo: MacroKeyCombo): Pair<Key, Set<String>> {
    val modifiers = mutableSetOf<String>()
    if (keyCombo.ctrl) modifiers.add("ctrl")
    if (keyCombo.alt) modifiers.add("alt")
    if (keyCombo.shift) modifiers.add("shift")
    if (keyCombo.meta) modifiers.add("meta")
    return Key(keyCombo.keyCode) to modifiers
}

internal fun MacroKeyCombo.toDisplayString(): String {
    val keyString = StringBuilder()
    if (ctrl) keyString.append("ctrl+")
    if (alt) keyString.append("alt+")
    if (shift) keyString.append("shift+")
    if (meta) keyString.append("meta+")
    keyString.append(Key(keyCode).getLabel())
    return keyString.toString()
}
