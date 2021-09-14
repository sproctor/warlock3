package cc.warlock.warlock3.stormfront.protocol.elements

import cc.warlock.warlock3.core.compass.DirectionType
import cc.warlock.warlock3.stormfront.protocol.BaseElementListener
import cc.warlock.warlock3.stormfront.protocol.StartElement
import cc.warlock.warlock3.stormfront.protocol.StormfrontDirectionEvent
import cc.warlock.warlock3.stormfront.protocol.StormfrontEvent

class DirHandler : BaseElementListener() {
    override fun startElement(element: StartElement): StormfrontEvent? {
        return element.attributes["value"]?.let { abbr ->
            DirectionType.fromAbbreviation(abbr)?.let {
                StormfrontDirectionEvent(it)
            }
        }
    }
}