package warlockfe.warlock3.compose.components

import androidx.compose.ui.input.pointer.PointerIcon

actual fun getResizeCursor(isHorizontal: Boolean): PointerIcon {
    val type = if (isHorizontal)
        android.view.PointerIcon.TYPE_HORIZONTAL_DOUBLE_ARROW
    else
        android.view.PointerIcon.TYPE_VERTICAL_DOUBLE_ARROW
    return PointerIcon(type)
}