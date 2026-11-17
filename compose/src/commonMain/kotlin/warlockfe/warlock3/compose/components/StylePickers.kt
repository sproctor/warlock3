package warlockfe.warlock3.compose.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import warlockfe.warlock3.core.text.Background
import warlockfe.warlock3.core.text.FontConfig
import warlockfe.warlock3.core.text.WarlockColor

// The pickers the style editors open. Each platform has its own: desktop opens them in dialog
// windows of their own, mobile as dialogs over the screen.

/** Picks a color, starting from [initialColor], or from white when that is null. */
@Composable
expect fun ColorPickerDialog(
    initialColor: Color?,
    onCloseRequest: () -> Unit,
    onColorSelect: (color: WarlockColor) -> Unit,
)

/** A button labeled [text] with a swatch of [color], which opens a color picker through [onClick]. */
@Composable
expect fun ColorPickerButton(
    text: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
)

/** Picks a font family, size and weight, starting from [current]. */
@Composable
expect fun FontPickerDialog(
    current: FontConfig?,
    onCloseRequest: () -> Unit,
    onSaveClick: (FontUpdate) -> Unit,
    monospaceOnly: Boolean = false,
)

/**
 * Picks a background: inherited, none, a fill color, or (with a skin [palette]) a skin slot, which
 * comes back through [onSelectSlot].
 */
@Composable
expect fun BackgroundPickerDialog(
    current: Background,
    inheritedBackground: Background,
    onSelect: (Background) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onSelectSlot: (String) -> Unit = {},
    palette: Map<String, WarlockColor> = emptyMap(),
)

/** Picks a text color from the skin's [palette], or hands off to a custom color through [onSelectCustom]. */
@Composable
expect fun ColorRefPickerDialog(
    palette: Map<String, WarlockColor>,
    onSelectSlot: (String) -> Unit,
    onSelectCustom: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
)
