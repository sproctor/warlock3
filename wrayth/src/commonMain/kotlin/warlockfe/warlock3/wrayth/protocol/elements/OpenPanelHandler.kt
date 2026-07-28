package warlockfe.warlock3.wrayth.protocol.elements

import warlockfe.warlock3.wrayth.protocol.BaseElementListener
import warlockfe.warlock3.wrayth.protocol.StartElement
import warlockfe.warlock3.wrayth.protocol.WraythEvent
import warlockfe.warlock3.wrayth.protocol.WraythPanelWindowEvent
import warlockfe.warlock3.wrayth.util.WraythPanelWindow

class OpenPanelHandler : BaseElementListener() {
    override fun startElement(element: StartElement): WraythEvent? {
        // We receive a location here that we're ignoring to have a bit more control over where things are placed
        val id = element.attributes["id"] ?: return null
        return WraythPanelWindowEvent(
            WraythPanelWindow(
                id = id,
                title = element.attributes["title"] ?: id,
                type = element.attributes["type"],
                location = element.attributes["location"],
                resident = element.attributes["resident"]?.startsWith(prefix = "t", ignoreCase = true) == true,
            ),
        )
    }
}
