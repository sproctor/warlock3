package warlockfe.warlock3.wrayth.protocol.elements

import warlockfe.warlock3.wrayth.protocol.BaseElementListener
import warlockfe.warlock3.wrayth.protocol.StartElement
import warlockfe.warlock3.wrayth.protocol.WraythModeEvent

class ModeHandler : BaseElementListener() {
    override fun startElement(element: StartElement): WraythModeEvent {
        return WraythModeEvent(id = element.attributes["id"])
    }
}