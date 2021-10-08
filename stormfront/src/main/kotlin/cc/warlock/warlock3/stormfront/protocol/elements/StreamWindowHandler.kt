package cc.warlock.warlock3.stormfront.protocol.elements

import cc.warlock.warlock3.core.Window
import cc.warlock.warlock3.core.WindowLocation
import cc.warlock.warlock3.stormfront.protocol.BaseElementListener
import cc.warlock.warlock3.stormfront.protocol.StartElement
import cc.warlock.warlock3.stormfront.protocol.StormfrontEvent
import cc.warlock.warlock3.stormfront.protocol.StormfrontStreamWindowEvent

class StreamWindowHandler : BaseElementListener() {
    override fun startElement(element: StartElement): StormfrontEvent? {
        val name = element.attributes["id"] ?: return null
        return StormfrontStreamWindowEvent(Window(
            name = name,
            title = element.attributes["title"] ?: name,
            location = when (element.attributes["location"]) {
                "center" -> WindowLocation.TOP
                "left" -> WindowLocation.LEFT
                "right" -> WindowLocation.RIGHT
                else -> WindowLocation.TOP
            },
            ifClosed = element.attributes["ifClosed"],
            styleIfClosed = element.attributes["styleIfClosed"]
        ))
    }
}