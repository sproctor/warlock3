package warlockfe.warlock3.wrayth.protocol.elements

import warlockfe.warlock3.wrayth.protocol.BaseElementListener
import warlockfe.warlock3.wrayth.protocol.StartElement
import warlockfe.warlock3.wrayth.protocol.WraythEvent
import warlockfe.warlock3.wrayth.protocol.WraythStreamEvent

class InvHandler : BaseElementListener() {
    override fun startElement(element: StartElement): WraythEvent? =
        element.attributes["id"]?.let { id ->
            WraythStreamEvent(id = id)
        }

    override fun endElement(): WraythEvent = WraythStreamEvent(null)
}
