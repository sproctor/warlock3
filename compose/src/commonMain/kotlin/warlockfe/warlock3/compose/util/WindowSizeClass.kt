package warlockfe.warlock3.compose.util

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Material 3 window width size classes. Derived from the available width (e.g. the `maxWidth` of a
 * [androidx.compose.foundation.layout.BoxWithConstraints]) rather than a platform helper, so it
 * works on every target with no extra dependency. The breakpoints follow the Material 3 spec.
 */
enum class WindowWidthSizeClass {
    Compact,
    Medium,
    Expanded,
    Large,
    ExtraLarge,
    ;

    companion object {
        val MediumLowerBound = 600.dp
        val ExpandedLowerBound = 840.dp
        val LargeLowerBound = 1200.dp
        val ExtraLargeLowerBound = 1600.dp

        fun fromWidth(width: Dp): WindowWidthSizeClass =
            when {
                width < MediumLowerBound -> Compact
                width < ExpandedLowerBound -> Medium
                width < LargeLowerBound -> Expanded
                width < ExtraLargeLowerBound -> Large
                else -> ExtraLarge
            }
    }
}

/** The three responsive mobile game-screen layouts. */
enum class MobileGameLayout {
    /** Compact + Medium width: every window is an M3 tab, one visible at a time. */
    Phone,

    /** Expanded width: the main window plus a tabbed pane for the other windows. */
    Tablet,

    /** Large + Extra-large width: the full drag-and-drop docking layout. */
    Large,
}

/** Maps a width size class to the mobile game layout that should be shown at that size. */
fun WindowWidthSizeClass.gameLayout(): MobileGameLayout =
    when (this) {
        WindowWidthSizeClass.Compact, WindowWidthSizeClass.Medium -> MobileGameLayout.Phone
        WindowWidthSizeClass.Expanded -> MobileGameLayout.Tablet
        WindowWidthSizeClass.Large, WindowWidthSizeClass.ExtraLarge -> MobileGameLayout.Large
    }
