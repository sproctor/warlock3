package warlockfe.warlock3.compose.util

import androidx.compose.ui.input.key.Key

expect fun Key.getLabel(): String

// The digit keys are named as words in the key table, which is fine for a stored macro key string
// and wrong on a button in the macro editor.
private val digitLabels =
    mapOf(
        "ZERO" to "0",
        "ONE" to "1",
        "TWO" to "2",
        "THREE" to "3",
        "FOUR" to "4",
        "FIVE" to "5",
        "SIX" to "6",
        "SEVEN" to "7",
        "EIGHT" to "8",
        "NINE" to "9",
    )

/**
 * Turns a [warlockfe.warlock3.compose.macros.KeyboardKeyMappings] code into something to put on a
 * button: `PAGE_UP` -> "Page Up", `NUMPAD_5` -> "Numpad 5", `SEVEN` -> "7".
 *
 * For platforms with no key-labelling API of their own to borrow.
 */
internal fun keyCodeLabel(code: String): String =
    digitLabels[code]
        ?: code
            .split('_')
            .joinToString(" ") { word ->
                word.lowercase().replaceFirstChar { it.uppercase() }
            }

private val allModifierKeys =
    arrayOf(
        Key.CtrlLeft,
        Key.CtrlRight,
        Key.AltLeft,
        Key.AltRight,
        Key.ShiftLeft,
        Key.ShiftRight,
        Key.MetaLeft,
        Key.MetaRight,
    )

fun Key.isModifier(): Boolean = allModifierKeys.contains(this)
