package warlockfe.warlock3.core.window

data class WindowInfo(
    val name: String,
    val title: String,
    val subtitle: String?,
    val windowType: WindowType,
    val showTimestamps: Boolean,
    val backgroundImage: ClientBackgroundImage?,
    val nameFilterOption: Boolean = false,
    // The dock the game's announcement suggested (via WindowLocation.fromProtocol), or null when it
    // named none or somewhere we do not dock. Only the default for a window the character has never
    // placed; the saved layout wins once one exists.
    val location: WindowLocation? = null,
    // Whether anything about this window may be saved. The game marks a panel resident when it is a
    // permanent fixture of the character's layout; a non-resident panel is transient, so it can be
    // moved, resized and closed while it is up but nothing about it is persisted and it stays out of
    // settings. Stream windows are always resident.
    val resident: Boolean = true,
)

enum class WindowLocation(
    val value: String,
) {
    TOP("top"),
    LEFT("left"),
    RIGHT("right"),
    MAIN("main"),
    BOTTOM("bottom"),
    ;

    companion object {
        /**
         * The dock a protocol `location` names, or null when the game names somewhere we do not put
         * panels. `statBar` and `quickBar` are chrome we draw ourselves (the vitals bar reads the
         * minivitals panel directly), and the protocol's `main` means floating over the main text
         * area, which we have no slot for, so it docks to the right instead. A panel never resolves
         * to [MAIN]: that is the main text window's own slot.
         */
        fun fromProtocol(value: String?): WindowLocation? =
            when (value?.lowercase()) {
                "left" -> LEFT
                "right", "main" -> RIGHT
                "top" -> TOP
                "bottom" -> BOTTOM
                else -> null
            }
    }
}

enum class WindowType {
    STREAM,
    PANEL,
}
