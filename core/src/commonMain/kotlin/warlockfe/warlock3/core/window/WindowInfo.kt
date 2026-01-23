package warlockfe.warlock3.core.window

data class WindowInfo(
    val name: String,
    val title: String,
    val subtitle: String?,
    val windowType: WindowType,
    val showTimestamps: Boolean,
)

enum class WindowLocation(val value: String) {
    TOP("top"),
    LEFT("left"),
    RIGHT("right"),
    MAIN("main"),
    BOTTOM("bottom"),
}

enum class WindowType {
    STREAM,
    DIALOG
}
