package cc.warlock.warlock3.core.window

import cc.warlock.warlock3.core.text.WarlockColor

data class Window(
    val name: String,
    val title: String,
    val subtitle: String?,
    val location: WindowLocation?,
    val position: Int?,
    val width: Int?,
    val height: Int?,
    val textColor: WarlockColor,
    val backgroundColor: WarlockColor,
    val fontFamily: String?,
    val fontSize: Double?,
)

enum class WindowLocation(val value: String) {
    TOP("top"),
    LEFT("left"),
    RIGHT("right"),
    MAIN("main"),
}