package warlockfe.warlock3.wrayth.protocol.elements

import warlockfe.warlock3.wrayth.protocol.BaseElementListener
import warlockfe.warlock3.wrayth.protocol.StartElement
import warlockfe.warlock3.wrayth.protocol.WraythClearDynaStreamEvent
import warlockfe.warlock3.wrayth.protocol.WraythEvent

class ClearDynaStreamHandler : BaseElementListener() {
    override fun startElement(element: StartElement): WraythEvent? = element.attributes["id"]?.let { WraythClearDynaStreamEvent(it) }
}
