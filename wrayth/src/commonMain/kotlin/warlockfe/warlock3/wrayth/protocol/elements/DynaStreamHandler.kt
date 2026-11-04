package warlockfe.warlock3.wrayth.protocol.elements

import warlockfe.warlock3.wrayth.protocol.BaseElementListener
import warlockfe.warlock3.wrayth.protocol.StartElement
import warlockfe.warlock3.wrayth.protocol.WraythDynaStreamEvent
import warlockfe.warlock3.wrayth.protocol.WraythEvent

class DynaStreamHandler : BaseElementListener() {
    override fun startElement(element: StartElement): WraythEvent? = element.attributes["id"]?.let { WraythDynaStreamEvent(it) }

    override fun endElement(): WraythEvent = WraythDynaStreamEvent(null)
}
