package warlockfe.warlock3.compose.components

import androidx.compose.ui.input.pointer.PointerIcon
import java.awt.Cursor

actual fun getResizeCursor(isHorizontal: Boolean): PointerIcon {
    return PointerIcon(Cursor(if (isHorizontal) Cursor.E_RESIZE_CURSOR else Cursor.S_RESIZE_CURSOR))
}