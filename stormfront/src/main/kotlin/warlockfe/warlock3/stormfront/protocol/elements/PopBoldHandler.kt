package warlockfe.warlock3.stormfront.protocol.elements

import warlockfe.warlock3.stormfront.protocol.*
import warlockfe.warlock3.stormfront.protocol.StormfrontEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontStyleEvent

class PopBoldHandler  : BaseElementListener() {
    override fun startElement(element: StartElement): StormfrontEvent {
        return StormfrontStyleEvent(null)
    }
}