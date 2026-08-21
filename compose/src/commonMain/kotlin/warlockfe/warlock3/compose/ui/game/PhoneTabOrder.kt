package warlockfe.warlock3.compose.ui.game

import kotlinx.serialization.json.Json
import warlockfe.warlock3.core.window.WindowInfo

/**
 * The phone tab strip's window list, as it stands for the connected character.
 *
 * Deliberately separate from the `WindowSettings.open` flag that feeds the docking layout: the
 * phone picks its own tabs, and adding one must not rearrange a dock the user is not looking at.
 */
sealed interface PhoneTabOrder {
    /** Nothing has been read from settings yet. */
    data object Loading : PhoneTabOrder

    /**
     * Nothing has ever been saved, so every tabbable window is a tab - what the phone did before
     * it had a window list of its own. Stays live: a window the game announces later still joins.
     */
    data object Auto : PhoneTabOrder

    /**
     * The user has picked their tabs. A window the game announces from here on does not join on
     * its own; the rail is how a window becomes a tab.
     */
    data class Explicit(
        val names: List<String>,
    ) : PhoneTabOrder
}

private val phoneTabJson = Json

internal fun encodePhoneTabOrder(names: List<String>): String = phoneTabJson.encodeToString(names)

/**
 * A missing value means [PhoneTabOrder.Auto] - nobody has chosen yet. So does an unreadable one:
 * falling back to "show everything" is the recoverable failure, where an empty strip would look
 * like the client had lost the character's windows.
 *
 * An empty list is not the same thing. It decodes to [PhoneTabOrder.Explicit] with no names, which
 * is a user who closed every tab they could, and leaves the main window alone in the strip.
 */
internal fun decodePhoneTabOrder(raw: String?): PhoneTabOrder =
    if (raw == null) {
        PhoneTabOrder.Auto
    } else {
        runCatching { phoneTabJson.decodeFromString<List<String>>(raw) }
            .fold(
                // Deduplicated because the strip keys its items by name: a repeat in a
                // hand-edited or otherwise damaged blob would crash it rather than degrade.
                onSuccess = { PhoneTabOrder.Explicit(it.distinct()) },
                onFailure = { PhoneTabOrder.Auto },
            )
    }

/**
 * The windows that may become a phone tab: the ones a character owns, minus those the game asked
 * the client to draw in its own chrome (the vitals bar, the command buttons), which are drawn
 * there rather than as a window and would tab to nothing.
 */
internal fun List<WindowInfo>.tabbable(): List<WindowInfo> = filter { it.resident && !it.location.isChrome }

/**
 * The names to show in the tab strip, in order.
 *
 * A saved name whose window has not been announced this session is left out of the strip but kept
 * in the saved order, so it returns to its old place when it comes back rather than to the end.
 * That is why this only reads the saved list and never rewrites it.
 */
internal fun reconcilePhoneTabs(
    order: PhoneTabOrder,
    tabbable: List<WindowInfo>,
): List<String> =
    when (order) {
        // Settings have not answered yet. Main is the truthful answer on a fresh connect - the game
        // has announced nothing else - so the strip fills in rather than flashing a wrong list.
        PhoneTabOrder.Loading -> {
            listOf(MAIN_WINDOW_NAME)
        }

        PhoneTabOrder.Auto -> {
            listOf(MAIN_WINDOW_NAME) +
                tabbable
                    .filter { it.name != MAIN_WINDOW_NAME }
                    .sortedBy { it.title }
                    .map { it.name }
        }

        is PhoneTabOrder.Explicit -> {
            val live = tabbable.mapTo(HashSet()) { it.name }
            val kept = order.names.filter { it == MAIN_WINDOW_NAME || it in live }
            // The main window is always a tab. It can be dragged, so it is only forced back when it
            // is missing entirely - never moved to the front of an order that already holds it.
            if (MAIN_WINDOW_NAME in kept) kept else listOf(MAIN_WINDOW_NAME) + kept
        }
    }

/**
 * Applies a move expressed in *displayed* positions to the *saved* order.
 *
 * The two can differ: a saved window the game has not announced yet is not on screen, so display
 * index 2 may be saved index 3. Rewriting the saved list from the displayed one would drop that
 * window for good, so the move is re-anchored on the displayed neighbour it landed next to.
 */
internal fun moveInSavedOrder(
    saved: List<String>,
    moved: String,
    anchor: String,
    forward: Boolean,
): List<String> {
    val result = saved.toMutableList()
    val from = result.indexOf(moved).takeIf { it >= 0 } ?: return saved
    result.removeAt(from)
    val at = result.indexOf(anchor).takeIf { it >= 0 } ?: return saved
    result.add(if (forward) at + 1 else at, moved)
    return result
}
