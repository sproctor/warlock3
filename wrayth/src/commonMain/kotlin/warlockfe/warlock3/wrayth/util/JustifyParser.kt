package warlockfe.warlock3.wrayth.util

import warlockfe.warlock3.core.client.PanelJustify

// Wrayth's default when a label carries no `justify` at all: DT_VCENTER or DT_CENTER.
private const val DEFAULT_JUSTIFY = 5

private const val DT_CENTER = 1
private const val DT_RIGHT = 2

/**
 * Reads a label's `justify` attribute, which aligns its text inside the width the label was given
 * (a label with no width hugs its text, so justifying it changes nothing).
 *
 * The attribute is not an enum but a raw Win32 `DrawText` format mask, which Wrayth parses as a
 * number and hands to the text drawing call as-is. Only two of its bits speak to horizontal
 * placement - DT_CENTER and DT_RIGHT - and center wins when both are set, so 0, 4 and 8 all go left
 * while 2, 6 and 10 all go right. A missing or empty `justify` draws with 5 (DT_VCENTER or
 * DT_CENTER) and so centers, while a non-empty value that is not a number reads as 0 and goes left.
 */
fun parseJustify(text: String?): PanelJustify {
    val flags = if (text.isNullOrEmpty()) DEFAULT_JUSTIFY else text.toIntOrNull() ?: 0
    return when {
        flags and DT_CENTER != 0 -> PanelJustify.Center
        flags and DT_RIGHT != 0 -> PanelJustify.Right
        else -> PanelJustify.Left
    }
}
