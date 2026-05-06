package warlockfe.warlock3.wrayth.protocol.elements

import warlockfe.warlock3.wrayth.protocol.BaseElementListener
import warlockfe.warlock3.wrayth.protocol.StartElement
import warlockfe.warlock3.wrayth.protocol.WraythComponentDefinitionEvent
import warlockfe.warlock3.wrayth.protocol.WraythComponentEndEvent
import warlockfe.warlock3.wrayth.protocol.WraythEvent

class CompDefHandler : BaseElementListener() {
    override fun startElement(element: StartElement): WraythEvent? = element.attributes["id"]?.let { WraythComponentDefinitionEvent(it) }

    override fun endElement(): WraythEvent = WraythComponentEndEvent
}
