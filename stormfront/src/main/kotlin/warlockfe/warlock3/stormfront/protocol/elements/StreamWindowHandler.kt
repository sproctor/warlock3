package warlockfe.warlock3.stormfront.protocol.elements

import warlockfe.warlock3.stormfront.protocol.BaseElementListener
import warlockfe.warlock3.stormfront.protocol.StartElement
import warlockfe.warlock3.stormfront.protocol.StormfrontEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontStreamWindowEvent
import warlockfe.warlock3.stormfront.stream.StormfrontWindow

class StreamWindowHandler : BaseElementListener() {
    override fun startElement(element: StartElement): StormfrontEvent? {
        // We receive a location here that we're ignoring to have a bit more control over where things are placed
        val name = element.attributes["id"] ?: return null
        return StormfrontStreamWindowEvent(
            StormfrontWindow(
                name = name,
                title = element.attributes["title"] ?: name,
                subtitle = element.attributes["subtitle"],
                ifClosed = element.attributes["ifClosed"],
                styleIfClosed = element.attributes["styleIfClosed"]
            )
        )
    }
}