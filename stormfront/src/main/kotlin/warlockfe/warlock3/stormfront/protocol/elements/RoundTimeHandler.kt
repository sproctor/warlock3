package warlockfe.warlock3.stormfront.protocol.elements

import warlockfe.warlock3.stormfront.protocol.BaseElementListener
import warlockfe.warlock3.stormfront.protocol.StartElement
import warlockfe.warlock3.stormfront.protocol.StormfrontEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontRoundTimeEvent

class RoundTimeHandler : BaseElementListener() {
    override fun startElement(element: StartElement): StormfrontEvent? {
        return element.attributes["value"]?.let {
            StormfrontRoundTimeEvent(time = it)
        }
    }
}