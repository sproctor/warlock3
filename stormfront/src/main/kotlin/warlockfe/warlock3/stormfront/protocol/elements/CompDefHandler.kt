package warlockfe.warlock3.stormfront.protocol.elements

import warlockfe.warlock3.stormfront.protocol.*
import warlockfe.warlock3.stormfront.protocol.StormfrontComponentDefinitionEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontEvent

class CompDefHandler : BaseElementListener() {
    override fun startElement(element: StartElement): StormfrontEvent? {
        return element.attributes["id"]?.let { StormfrontComponentDefinitionEvent(it) }
    }

    override fun endElement(): StormfrontEvent {
        return StormfrontComponentEndEvent
    }
}