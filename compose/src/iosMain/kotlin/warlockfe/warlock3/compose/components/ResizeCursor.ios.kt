package warlockfe.warlock3.compose.components

import androidx.compose.ui.input.pointer.PointerIcon

actual fun getResizeCursor(isHorizontal: Boolean): PointerIcon {
    return PointerIcon.Hand // TODO: use resize pointer
}