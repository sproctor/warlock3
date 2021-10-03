package cc.warlock.warlock3.stormfront.protocol.elements

import cc.warlock.warlock3.stormfront.protocol.BaseElementListener
import cc.warlock.warlock3.stormfront.protocol.StartElement
import cc.warlock.warlock3.stormfront.protocol.StormfrontCastTimeEvent
import cc.warlock.warlock3.stormfront.protocol.StormfrontEvent

class CastTimeHandler : BaseElementListener() {
    override fun startElement(element: StartElement): StormfrontEvent? {
        return element.attributes["value"]?.let {
            StormfrontCastTimeEvent(time = it)
        }
    }
}