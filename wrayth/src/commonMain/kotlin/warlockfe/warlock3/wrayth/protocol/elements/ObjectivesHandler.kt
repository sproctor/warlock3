package warlockfe.warlock3.wrayth.protocol.elements

import warlockfe.warlock3.wrayth.protocol.BaseElementListener
import warlockfe.warlock3.wrayth.protocol.StartElement
import warlockfe.warlock3.wrayth.protocol.WraythEvent
import warlockfe.warlock3.wrayth.protocol.WraythObjectivesEndEvent
import warlockfe.warlock3.wrayth.protocol.WraythObjectivesStartEvent

class ObjectivesHandler : BaseElementListener() {
    override fun startElement(element: StartElement): WraythEvent = WraythObjectivesStartEvent(element.attributes["action"])

    override fun endElement(): WraythEvent = WraythObjectivesEndEvent
}
