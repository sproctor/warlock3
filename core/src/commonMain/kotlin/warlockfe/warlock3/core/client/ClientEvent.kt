package warlockfe.warlock3.core.client

import com.eygraber.uri.Uri
import kotlinx.collections.immutable.ImmutableSet
import warlockfe.warlock3.core.compass.DirectionType
import warlockfe.warlock3.core.text.StyledString
import warlockfe.warlock3.core.window.WindowInfo

sealed interface ClientEvent
data class ClientTextEvent(val text: String) : ClientEvent
data class ClientCompassEvent(val directions: ImmutableSet<DirectionType>) : ClientEvent
data object ClientNavEvent : ClientEvent
data class ClientBackgroundImageEvent(
    val windowName: String?,
    val image: String?,
    val mode: BackgroundImageMode = BackgroundImageMode.HEIGHT_FILL,
    val gradientStart: Int = 0,
    val gradientEnd: Int = 100,
    val opacity: Int = 100,
    val horizontalAlignment: BackgroundImageHorizontalAlignment = BackgroundImageHorizontalAlignment.CENTER,
    val verticalAlignment: BackgroundImageVerticalAlignment = BackgroundImageVerticalAlignment.MIDDLE,
    val clearAll: Boolean = false,
) : ClientEvent
data object ClientPromptEvent : ClientEvent
data class ClientOpenUrlEvent(val url: Uri) : ClientEvent
data class ClientWindowInfoEvent(val info: WindowInfo) : ClientEvent

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
