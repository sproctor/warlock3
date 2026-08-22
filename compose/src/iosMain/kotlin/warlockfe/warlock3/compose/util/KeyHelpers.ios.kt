package warlockfe.warlock3.compose.util

import androidx.compose.ui.input.key.Key
import warlockfe.warlock3.compose.macros.KeyboardKeyMappings

/**
 * UIKit has nothing like AWT's `getKeyText` or Android's `displayLabel` - a `UIKey` carries a
 * character for the keys that produce one and nothing for the rest - so the label comes from the
 * same table the macro key strings are built from, tidied for display by [keyCodeLabel].
 *
 * A key the table does not name falls back to its code rather than to a constant placeholder, so
 * two different unmapped bindings still read as two different bindings in the editor.
 */
actual fun Key.getLabel(): String =
    KeyboardKeyMappings.getCode(this)?.let(::keyCodeLabel) ?: "Key 0x${keyCode.toString(16)}"
