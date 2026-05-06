package warlockfe.warlock3.wrayth.protocol.elements

import warlockfe.warlock3.wrayth.protocol.BaseElementListener
import warlockfe.warlock3.wrayth.protocol.StartElement
import warlockfe.warlock3.wrayth.protocol.WraythComponentEndEvent
import warlockfe.warlock3.wrayth.protocol.WraythComponentStartEvent
import warlockfe.warlock3.wrayth.protocol.WraythEvent

class ComponentHandler : BaseElementListener() {
    override fun startElement(element: StartElement): WraythEvent = WraythComponentStartEvent(element.attributes["id"] ?: "")

    override fun endElement(): WraythEvent = WraythComponentEndEvent
}
