package warlockfe.warlock3.wrayth.protocol.elements

import warlockfe.warlock3.wrayth.protocol.BaseElementListener
import warlockfe.warlock3.wrayth.protocol.StartElement
import warlockfe.warlock3.wrayth.protocol.WraythEvent
import warlockfe.warlock3.wrayth.protocol.WraythStreamWindowEvent
import warlockfe.warlock3.wrayth.util.WraythStreamWindow

class ContainerHandler : BaseElementListener() {
    override fun startElement(element: StartElement): WraythEvent? {
        val name = element.attributes["id"] ?: return null
        return WraythStreamWindowEvent(
            WraythStreamWindow(
                id = name,
                title = element.attributes["title"] ?: "",
                subtitle = null,
                ifClosed = "",
                styleIfClosed = null,
                timestamp = false,
            )
        )
    }
}