package cc.warlock.warlock3.core

data class Window(
    val name: String,
    val title: String,
    val styleIfClosed: String?,
    val ifClosed: String?,
)

enum class WindowLocation {
    TOP,
    LEFT,
    RIGHT
}