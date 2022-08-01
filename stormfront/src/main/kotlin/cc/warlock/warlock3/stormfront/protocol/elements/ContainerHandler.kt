package cc.warlock.warlock3.stormfront.protocol.elements

import cc.warlock.warlock3.stormfront.protocol.BaseElementListener
import cc.warlock.warlock3.stormfront.protocol.StartElement
import cc.warlock.warlock3.stormfront.protocol.StormfrontEvent
import cc.warlock.warlock3.stormfront.protocol.StormfrontStreamWindowEvent
import cc.warlock.warlock3.stormfront.stream.StormfrontWindow

class ContainerHandler : BaseElementListener() {
    override fun startElement(element: StartElement): StormfrontEvent? {
        val name = element.attributes["id"] ?: return null
        return StormfrontStreamWindowEvent(
            StormfrontWindow(
                name = name,
                title = element.attributes["title"] ?: "",
                subtitle = null,
                ifClosed = "",
                styleIfClosed = null,
            )
        )
    }
}