package cc.warlock.warlock3.stormfront.protocol.elements

import cc.warlock.warlock3.stormfront.protocol.BaseElementListener
import cc.warlock.warlock3.stormfront.protocol.StartElement
import cc.warlock.warlock3.stormfront.protocol.StormfrontEvent
import cc.warlock.warlock3.stormfront.protocol.StormfrontStreamEvent

class DynaStreamHandler : BaseElementListener() {
    override fun startElement(element: StartElement): StormfrontEvent {
        return StormfrontStreamEvent(element.attributes["id"])
    }

    override fun endElement(): StormfrontEvent {
        return StormfrontStreamEvent(null)
    }
}