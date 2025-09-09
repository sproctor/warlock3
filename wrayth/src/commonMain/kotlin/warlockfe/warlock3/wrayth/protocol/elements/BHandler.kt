package warlockfe.warlock3.wrayth.protocol.elements

import warlockfe.warlock3.core.text.WarlockStyle
import warlockfe.warlock3.wrayth.protocol.BaseElementListener
import warlockfe.warlock3.wrayth.protocol.StartElement
import warlockfe.warlock3.wrayth.protocol.WraythEvent
import warlockfe.warlock3.wrayth.protocol.WraythPopStyleEvent
import warlockfe.warlock3.wrayth.protocol.WraythPushStyleEvent

class BHandler : BaseElementListener() {
    override fun startElement(element: StartElement): WraythEvent {
        return WraythPushStyleEvent(WarlockStyle.Bold)
    }

    override fun endElement(): WraythEvent {
        return WraythPopStyleEvent
    }
}