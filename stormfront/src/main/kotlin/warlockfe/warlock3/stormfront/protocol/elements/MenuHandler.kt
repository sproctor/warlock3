package warlockfe.warlock3.stormfront.protocol.elements

import warlockfe.warlock3.stormfront.protocol.BaseElementListener
import warlockfe.warlock3.stormfront.protocol.StartElement
import warlockfe.warlock3.stormfront.protocol.StormfrontEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontMenuEndEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontMenuStartEvent

class MenuHandler : BaseElementListener() {
    override fun startElement(element: StartElement): StormfrontEvent {
        return StormfrontMenuStartEvent(element.attributes["id"]?.toIntOrNull())
    }

    override fun endElement(): StormfrontEvent {
        return StormfrontMenuEndEvent
    }
}
