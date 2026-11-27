package warlockfe.warlock3.compose.ui.settings

// The main window always has a row of its own, under "Shown".
internal const val MAIN_WINDOW = "main"

/**
 * Whether a window the user asked for still shows as a placeholder row, under "Saved settings (not
 * shown)": until it has saved settings of its own. It stays there even once the game announces it, so
 * a row the user is looking at doesn't jump into the collapsed "Other windows" group.
 */
internal fun showsPendingRow(
    name: String,
    savedNames: Set<String>,
): Boolean = name != MAIN_WINDOW && name !in savedNames

/** Where [settleRevealRequests] put the requested windows. */
internal data class RevealOutcome(
    val openOtherWindows: Boolean,
    val newPending: List<String>,
)

/**
 * Places the windows the user asked to see (added by name, or targeted by a "Window settings" action),
 * or returns null to wait while the character's saved settings are still loading: until then every
 * saved window looks unconfigured. A request for one of [hiddenNames], the windows in the collapsed
 * "Other windows" group, opens that group. A request for a window with no saved settings that isn't
 * there becomes a placeholder row. Any other request already has a row where it is.
 */
internal fun settleRevealRequests(
    requests: List<String>,
    settingsLoaded: Boolean,
    hiddenNames: Set<String>,
    savedNames: Set<String>,
): RevealOutcome? {
    if (!settingsLoaded) return null
    return RevealOutcome(
        openOtherWindows = requests.any { it in hiddenNames },
        newPending = requests.filter { it !in hiddenNames && showsPendingRow(it, savedNames) },
    )
}
