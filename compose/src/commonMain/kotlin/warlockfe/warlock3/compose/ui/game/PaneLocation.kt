package warlockfe.warlock3.compose.ui.game

/**
 * Which edge the tablet layout's secondary window pane is docked against. A user preference, saved
 * per character by [name] - nothing to do with the game's window announcements, which speak
 * [warlockfe.warlock3.core.window.WindowLocation] and know only the four places the real client
 * accepts.
 */
enum class PaneLocation {
    LEFT,
    RIGHT,
    TOP,
    BOTTOM,
}
