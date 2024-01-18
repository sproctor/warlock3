package warlockfe.warlock3.stormfront.protocol.elements

import warlockfe.warlock3.stormfront.protocol.BaseElementListener
import warlockfe.warlock3.stormfront.protocol.StormfrontCompassEndEvent

class CompassHandler : BaseElementListener() {
    override fun endElement() = StormfrontCompassEndEvent
}