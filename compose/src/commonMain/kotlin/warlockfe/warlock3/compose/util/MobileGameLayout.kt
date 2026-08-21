package warlockfe.warlock3.compose.util

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The two responsive mobile game-screen layouts. */
enum class MobileGameLayout(
    val breakpoint: Dp,
) {
    /** Compact + Medium width: the user's chosen windows as tabs, one visible at a time. */
    Phone(0.dp),

    /** Expanded and wider: the full drag-and-drop docking layout. */
    Large(840.dp),
    ;

    companion object {
        fun fromWidth(width: Dp): MobileGameLayout =
            when {
                width >= Large.breakpoint -> Large
                else -> Phone
            }
    }
}
