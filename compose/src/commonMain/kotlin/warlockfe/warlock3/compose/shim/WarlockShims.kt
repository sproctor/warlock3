package warlockfe.warlock3.compose.shim

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp

// The controls a shared screen is built from. Each platform draws them in its own design system:
// Jewel on desktop, Material 3 on mobile. A screen that is the same on both apart from which
// library draws its controls belongs in commonMain, built from these.

/** The color text and icons draw in when given none: the platform theme's current content color. */
@get:Composable
expect val warlockContentColor: Color

/**
 * The side of the arrow that expands a collapsible row. The row's content is indented by this plus
 * the gap after the arrow, so it lines up under the row's label.
 */
expect val disclosureArrowSize: Dp

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
 * A read-only dropdown of [items], showing [selected] as [dropdownSelectedIndex] picks it: nothing
 * for a null that is not one of the items, the first item for a value no longer in the list.
 * [textStyle] replaces the platform's default style for the labels.
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

/**
 * A single-line text field. [onSubmit], when given, runs on Enter (or the keyboard's Done key).
 * [placeholder] names the field: desktop shows it while the field is empty, mobile as the field's
 * label.
 */
@Composable
expect fun WarlockTextField(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    onSubmit: (() -> Unit)? = null,
    placeholder: String? = null,
)
