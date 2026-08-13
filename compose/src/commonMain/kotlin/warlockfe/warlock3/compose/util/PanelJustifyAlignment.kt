package warlockfe.warlock3.compose.util

import androidx.compose.ui.Alignment
import warlockfe.warlock3.core.client.PanelJustify

/**
 * The alignment that justifies a label's text within its box. Vertically centered either way, since
 * `justify` only speaks to the horizontal placement.
 */
internal fun PanelJustify.toAlignment(): Alignment =
    when (this) {
        PanelJustify.Left -> Alignment.CenterStart
        PanelJustify.Center -> Alignment.Center
        PanelJustify.Right -> Alignment.CenterEnd
    }
