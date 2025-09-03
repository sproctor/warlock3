package warlockfe.warlock3.wrayth.protocol.elements

import warlockfe.warlock3.wrayth.protocol.*
import warlockfe.warlock3.wrayth.protocol.WraythComponentEndEvent
import warlockfe.warlock3.wrayth.protocol.WraythComponentStartEvent
import warlockfe.warlock3.wrayth.protocol.WraythEvent

class ComponentHandler : BaseElementListener() {
    override fun startElement(element: StartElement): WraythEvent {
        return WraythComponentStartEvent(element.attributes["id"] ?: "")
    }

    override fun endElement(): WraythEvent {
        return WraythComponentEndEvent
    }
}