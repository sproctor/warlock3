package warlockfe.warlock3.wrayth.protocol.elements

import warlockfe.warlock3.wrayth.protocol.BaseElementListener
import warlockfe.warlock3.wrayth.protocol.StartElement
import warlockfe.warlock3.wrayth.protocol.WraythCastTimeEvent
import warlockfe.warlock3.wrayth.protocol.WraythEvent

class CastTimeHandler : BaseElementListener() {
    override fun startElement(element: StartElement): WraythEvent? =
        element.attributes["value"]?.let {
            WraythCastTimeEvent(time = it)
        }
}
