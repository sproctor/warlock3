package warlockfe.warlock3.compose.ui.game

// The docking library floats windows in AWT windows, and the desktop app runs on Nucleus's Tao
// windows with no AWT in the process, so game windows stay docked.
internal actual val hostSupportsFloatingWindows: Boolean = false
