package warlockfe.warlock3.core.window

data class WindowInfo(
    val name: String,
    val title: String,
    val subtitle: String?,
    val windowType: WindowType,
    val showTimestamps: Boolean,
    val backgroundImage: ClientBackgroundImage?,
    val nameFilterOption: Boolean = false,
    // Where the game's announcement asked for this window (via WindowLocation.fromProtocol). Only
    // the default for a window the character has never placed; the saved layout wins once one
    // exists.
    val location: WindowLocation = WindowLocation.CENTER,
    // Whether anything about this window may be saved. The game marks a panel resident when it is a
    // permanent fixture of the character's layout; a non-resident panel is transient, so it can be
    // moved, resized and closed while it is up but nothing about it is persisted and it stays out of
    // settings. Stream windows are always resident.
    val resident: Boolean = true,
)

/**
 * A place the game can ask a window to go. These six are the whole vocabulary the real client
 * accepts: anything else on a `streamWindow` or `openDialog` comes straight back over the socket as
 * `_error bad location in <the tag>`, verified in utils/wrayth-lab. They are spelled exactly as the
 * protocol spells them, camel case included - `statBar` is a location, `statusbar` is an error.
 *
 * [CENTER] is the client's fallback, not just one option among six. A window with no `location` at
 * all, and a window whose location was rejected, both end up in exactly the same place: floating
 * over the main text area, at the same default spot (lab-verified by opening one of each and
 * finding them stacked on top of one another). So [fromProtocol] answers for every announcement,
 * and everything it does not recognise lands where the real client puts it.
 *
 * [STATBAR] and [QUICKBAR] are not places in the window layout: they name the client's own chrome,
 * which holds the vitals bars and the command buttons. We draw both ourselves (the vitals read the
 * minivitals panel directly), so a panel that asks for one never becomes a window at all - it is
 * neither docked nor offered in the window list, since docking it would draw the same bars twice.
 */
enum class WindowLocation(
    val value: String,
    /** Whether this names the client's own chrome rather than a place in the window layout. */
    val isChrome: Boolean = false,
) {
    LEFT("left"),
    RIGHT("right"),
    CENTER("center"),
    DETACHED("detach"),
    STATBAR("statBar", isChrome = true),
    QUICKBAR("quickBar", isChrome = true),
    ;

    companion object {
        /**
         * Where a protocol `location` asks for. An absent location and one the real client would
         * reject both give [CENTER], which is where that client leaves them - in practice the
         * rejected one is the `location='main'` Lich scripts use for panels like UberBar and
         * CreatureWindow.
         */
        fun fromProtocol(value: String?): WindowLocation {
            if (value == null) return CENTER
            return entries.firstOrNull { it.value == value } ?: CENTER
        }
    }
}

enum class WindowType {
    STREAM,
    PANEL,
}
