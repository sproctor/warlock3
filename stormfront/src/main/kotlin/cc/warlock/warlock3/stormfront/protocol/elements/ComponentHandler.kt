package cc.warlock.warlock3.stormfront.protocol.elements

import cc.warlock.warlock3.stormfront.protocol.*

class ComponentHandler : BaseElementListener() {
    override fun startElement(element: StartElement): StormfrontEvent {
        return StormfrontComponentStartEvent(element.attributes["id"] ?: "")
    }

    override fun endElement(element: EndElement): StormfrontEvent {
        return StormfrontComponentEndEvent
    }
}