package warlockfe.warlock3.wrayth.protocol.elements

import warlockfe.warlock3.wrayth.protocol.BaseElementListener
import warlockfe.warlock3.wrayth.protocol.StartElement
import warlockfe.warlock3.wrayth.protocol.WraythEvent
import warlockfe.warlock3.wrayth.protocol.WraythMenuEndEvent
import warlockfe.warlock3.wrayth.protocol.WraythMenuStartEvent

class MenuHandler : BaseElementListener() {
    override fun startElement(element: StartElement): WraythEvent {
        return WraythMenuStartEvent(element.attributes["id"]?.toIntOrNull())
    }

    override fun endElement(): WraythEvent {
        return WraythMenuEndEvent
    }
}
