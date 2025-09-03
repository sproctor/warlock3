package warlockfe.warlock3.wrayth.protocol.elements

import warlockfe.warlock3.wrayth.protocol.*
import warlockfe.warlock3.wrayth.protocol.WraythComponentDefinitionEvent
import warlockfe.warlock3.wrayth.protocol.WraythEvent

class CompDefHandler : BaseElementListener() {
    override fun startElement(element: StartElement): WraythEvent? {
        return element.attributes["id"]?.let { WraythComponentDefinitionEvent(it) }
    }

    override fun endElement(): WraythEvent {
        return WraythComponentEndEvent
    }
}