package warlockfe.warlock3.wrayth.protocol.elements

import warlockfe.warlock3.wrayth.protocol.BaseElementListener
import warlockfe.warlock3.wrayth.protocol.StartElement
import warlockfe.warlock3.wrayth.protocol.WraythEvent
import warlockfe.warlock3.wrayth.protocol.WraythStreamWindowEvent
import warlockfe.warlock3.wrayth.util.WraythStreamWindow

class StreamWindowHandler : BaseElementListener() {
    override fun startElement(element: StartElement): WraythEvent? {
        // We receive a location here that we're ignoring to have a bit more control over where things are placed
        val id = element.attributes["id"] ?: return null
        return WraythStreamWindowEvent(
            WraythStreamWindow(
                id = id,
                title = element.attributes["title"] ?: id,
                subtitle = element.attributes["subtitle"],
                ifClosed = element.attributes["ifClosed"],
                styleIfClosed = element.attributes["styleIfClosed"],
                timestamp = element.attributes["timestamp"] != null,
            )
        )
    }
}
