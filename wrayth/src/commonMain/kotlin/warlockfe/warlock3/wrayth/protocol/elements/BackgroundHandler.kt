package warlockfe.warlock3.wrayth.protocol.elements

import warlockfe.warlock3.core.window.BackgroundImageHorizontalAlignment
import warlockfe.warlock3.core.window.BackgroundImageMode
import warlockfe.warlock3.core.window.BackgroundImageVerticalAlignment
import warlockfe.warlock3.wrayth.protocol.BaseElementListener
import warlockfe.warlock3.wrayth.protocol.StartElement
import warlockfe.warlock3.wrayth.protocol.WraythBackgroundEvent
import warlockfe.warlock3.wrayth.protocol.WraythEvent

class BackgroundHandler : BaseElementListener() {
    override fun startElement(element: StartElement): WraythEvent {
        val mode = element.attributes["mode"].toBackgroundImageMode()
        val opacity = element.attributes["opacity"].toPercent(default = 100)
        return WraythBackgroundEvent(
            windowName = element.attributes["window"] ?: "main",
            image = element.attributes["img"]?.takeIf { it.isNotBlank() && it != "0" },
            mode = mode,
            gradientStart = element.attributes["start"].toPercent(default = 0),
            gradientEnd = element.attributes["end"].toPercent(default = 100),
            opacity = opacity,
            horizontalAlignment = element.attributes["align"].toHorizontalAlignment(),
            verticalAlignment = element.attributes["valign"].toVerticalAlignment(),
        )
    }

    private fun String?.toBackgroundImageMode(): BackgroundImageMode =
        when (this?.trim()?.lowercase()) {
            "fill" -> BackgroundImageMode.FILL
            "hfill" -> BackgroundImageMode.HEIGHT_FILL
            "wfill" -> BackgroundImageMode.WIDTH_FILL
            "full" -> BackgroundImageMode.FULL
            "gradient" -> BackgroundImageMode.GRADIENT
            else -> BackgroundImageMode.HEIGHT_FILL
        }

    private fun String?.toPercent(default: Int): Int = this?.trim()?.toIntOrNull()?.coerceIn(0, 100) ?: default

    private fun String?.toHorizontalAlignment(): BackgroundImageHorizontalAlignment =
        when (this?.trim()?.lowercase()) {
            "left" -> BackgroundImageHorizontalAlignment.LEFT
            "right" -> BackgroundImageHorizontalAlignment.RIGHT
            else -> BackgroundImageHorizontalAlignment.CENTER
        }

    private fun String?.toVerticalAlignment(): BackgroundImageVerticalAlignment =
        when (this?.trim()?.lowercase()) {
            "top" -> BackgroundImageVerticalAlignment.TOP
            "bottom" -> BackgroundImageVerticalAlignment.BOTTOM
            else -> BackgroundImageVerticalAlignment.MIDDLE
        }
}
