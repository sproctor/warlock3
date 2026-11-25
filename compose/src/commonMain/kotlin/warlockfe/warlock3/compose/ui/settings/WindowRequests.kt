package warlockfe.warlock3.compose.ui.settings

// The main window always has a row of its own, under "Shown".
internal const val MAIN_WINDOW = "main"

/**
 * Whether [name] needs a placeholder row, standing in for a window Warlock knows nothing about yet:
 * one with no saved settings ([savedNames]) that the game hasn't announced ([announcedNames]).
 */
internal fun needsPendingRow(
    name: String,
    savedNames: Set<String>,
    announcedNames: Set<String>,
): Boolean = name != MAIN_WINDOW && name !in savedNames && name !in announcedNames

/** What [settleRevealRequests] decided. */
internal data class RevealOutcome(
    val openOtherWindows: Boolean,
    val unsettled: List<String>,
)

/**
 * Settles the windows the user asked to see (added by name, or targeted by a "Window settings"
 * action) once it's known which group each one is in. A request for one of [hiddenNames], the
 * windows in the collapsed "Other windows" group, opens that group. A request for any other saved
 * or announced window is settled where it is. The rest stay [RevealOutcome.unsettled], since their
 * settings or the game's announcement may still be on the way.
 */
internal fun settleRevealRequests(
    requests: List<String>,
    hiddenNames: Set<String>,
    savedNames: Set<String>,
    announcedNames: Set<String>,
): RevealOutcome =
    RevealOutcome(
        openOtherWindows = requests.any { it in hiddenNames },
        unsettled = requests.filter { needsPendingRow(it, savedNames, announcedNames) },
    )
