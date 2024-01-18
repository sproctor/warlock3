package warlockfe.warlock3.stormfront.protocol.elements

import warlockfe.warlock3.stormfront.protocol.BaseElementListener
import warlockfe.warlock3.stormfront.protocol.StartElement
import warlockfe.warlock3.stormfront.protocol.StormfrontCastTimeEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontEvent

class CastTimeHandler : BaseElementListener() {
    override fun startElement(element: StartElement): StormfrontEvent? {
        return element.attributes["value"]?.let {
            StormfrontCastTimeEvent(time = it)
        }
    }
}