package cc.warlock.warlock3.core.window

data class Window(
    val name: String,
    val title: String,
    val subtitle: String?,
    val location: WindowLocation?,
    val position: Int?,
    val width: Int?,
    val height: Int?,
)

enum class WindowLocation(val value: String) {
    TOP("top"),
    LEFT("left"),
    RIGHT("right"),
    MAIN("main"),
}