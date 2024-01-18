package warlockfe.warlock3.stormfront.protocol.elements

import warlockfe.warlock3.stormfront.protocol.*
import warlockfe.warlock3.stormfront.protocol.StormfrontComponentEndEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontComponentStartEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontEvent

class ComponentHandler : BaseElementListener() {
    override fun startElement(element: StartElement): StormfrontEvent {
        return StormfrontComponentStartEvent(element.attributes["id"] ?: "")
    }

    override fun endElement(): StormfrontEvent {
        return StormfrontComponentEndEvent
    }
}