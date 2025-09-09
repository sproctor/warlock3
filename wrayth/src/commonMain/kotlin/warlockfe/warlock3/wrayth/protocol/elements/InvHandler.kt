package warlockfe.warlock3.wrayth.protocol.elements

import warlockfe.warlock3.wrayth.protocol.*
import warlockfe.warlock3.wrayth.protocol.WraythEvent
import warlockfe.warlock3.wrayth.protocol.WraythStreamEvent

class InvHandler : BaseElementListener() {
    override fun startElement(element: StartElement): WraythEvent? {
        return element.attributes["id"]?.let { id ->
            WraythStreamEvent(id = id)
        }
    }

    override fun endElement(): WraythEvent {
        return WraythStreamEvent(null)
    }
}