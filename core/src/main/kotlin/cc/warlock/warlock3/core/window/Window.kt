package cc.warlock.warlock3.core.window

data class Window(
    val name: String,
    val title: String,
    val subtitle: String,
    val styleIfClosed: String?,
    val ifClosed: String?,
    val location: WindowLocation,
)

enum class WindowLocation {
    TOP,
    LEFT,
    RIGHT,
    MAIN,
}