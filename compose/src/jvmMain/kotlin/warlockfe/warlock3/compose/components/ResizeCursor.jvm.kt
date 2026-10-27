package warlockfe.warlock3.compose.components

import androidx.compose.ui.input.pointer.PointerIcon
import dev.nucleusframework.window.tao.TaoPointerIcons

actual fun getResizeCursor(isHorizontal: Boolean): PointerIcon =
    if (isHorizontal) TaoPointerIcons.ResizeEastWest else TaoPointerIcons.ResizeNorthSouth
