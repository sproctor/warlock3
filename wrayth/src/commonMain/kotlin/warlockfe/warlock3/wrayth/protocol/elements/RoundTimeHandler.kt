package warlockfe.warlock3.wrayth.protocol.elements

import warlockfe.warlock3.wrayth.protocol.BaseElementListener
import warlockfe.warlock3.wrayth.protocol.StartElement
import warlockfe.warlock3.wrayth.protocol.WraythEvent
import warlockfe.warlock3.wrayth.protocol.WraythRoundTimeEvent

class RoundTimeHandler : BaseElementListener() {
    override fun startElement(element: StartElement): WraythEvent? {
        return element.attributes["value"]?.let {
            WraythRoundTimeEvent(time = it)
        }
    }
}