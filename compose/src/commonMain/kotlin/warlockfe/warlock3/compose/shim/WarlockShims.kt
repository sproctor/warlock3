package warlockfe.warlock3.compose.shim

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle

// The controls a shared screen is built from. Each platform draws them in its own design system:
// Jewel on desktop, Material 3 on mobile. A screen that is the same on both apart from which
// library draws its controls belongs in commonMain, built from these.

/** A line of text in the platform's body style. [color] defaults to the current content color. */
@Composable
expect fun WarlockText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
)

/** De-emphasized text: a hint or note beside a control, drawn dimmer than [WarlockText]. */
@Composable
expect fun WarlockSecondaryText(
    text: String,
    modifier: Modifier = Modifier,
)

/** The primary button of a form or dialog. */
@Composable
expect fun WarlockButton(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
)

/** A secondary button: everything that is not the one primary action. */
@Composable
expect fun WarlockOutlinedButton(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
)

/** A checkbox with its label; clicking either toggles it. */
@Composable
expect fun WarlockCheckboxRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    text: String,
    modifier: Modifier = Modifier,
)

/**
 * A read-only dropdown of [items], showing [selected]. A null [selected] that is not itself one of
 * the items shows nothing selected (a game-sent selection that names no option, say); null is a
 * real item where T is nullable, such as the character selector's "Global". [textStyle] replaces
 * the platform's default style for the labels.
 */
@Composable
expect fun <T> WarlockDropdownSelect(
    items: List<T>,
    selected: T?,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    itemLabelBuilder: (T) -> String = { it.toString() },
    textStyle: TextStyle? = null,
)
