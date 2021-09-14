package cc.warlock.warlock3.stormfront.protocol.elements

import cc.warlock.warlock3.stormfront.protocol.BaseElementListener
import cc.warlock.warlock3.stormfront.protocol.EndElement
import cc.warlock.warlock3.stormfront.protocol.StormfrontCompassEndEvent

class CompassHandler : BaseElementListener() {
    override fun endElement(element: EndElement) = StormfrontCompassEndEvent
}