package warlockfe.warlock3.core.window

data class ClientBackgroundImage(
    val image: String,
    val mode: BackgroundImageMode,
    val gradientStart: Int,
    val gradientEnd: Int,
    val opacity: Int,
    val horizontalAlignment: BackgroundImageHorizontalAlignment,
    val verticalAlignment: BackgroundImageVerticalAlignment,
)

enum class BackgroundImageMode {
    FILL,
    HEIGHT_FILL,
    WIDTH_FILL,
    FULL,
    GRADIENT,
}

enum class BackgroundImageHorizontalAlignment {
    LEFT,
    CENTER,
    RIGHT,
}

enum class BackgroundImageVerticalAlignment {
    TOP,
    MIDDLE,
    BOTTOM,
}
