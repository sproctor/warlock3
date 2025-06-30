package warlockfe.warlock3.stormfront.protocol.elements

import warlockfe.warlock3.stormfront.protocol.BaseElementListener
import warlockfe.warlock3.stormfront.protocol.StartElement
import warlockfe.warlock3.stormfront.protocol.StormfrontEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontStreamWindowEvent
import warlockfe.warlock3.stormfront.util.StormfrontStreamWindow

class ContainerHandler : BaseElementListener() {
    override fun startElement(element: StartElement): StormfrontEvent? {
        val name = element.attributes["id"] ?: return null
        return StormfrontStreamWindowEvent(
            StormfrontStreamWindow(
                id = name,
                title = element.attributes["title"] ?: "",
                subtitle = null,
                ifClosed = "",
                styleIfClosed = null,
            )
        )
    }
}