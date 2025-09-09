package warlockfe.warlock3.wrayth.protocol.elements

import warlockfe.warlock3.wrayth.protocol.BaseElementListener
import warlockfe.warlock3.wrayth.protocol.StartElement
import warlockfe.warlock3.wrayth.protocol.WraythEvent
import warlockfe.warlock3.wrayth.protocol.WraythPopStyleEvent

class PopBoldHandler  : BaseElementListener() {
    override fun startElement(element: StartElement): WraythEvent {
        return WraythPopStyleEvent
    }
}