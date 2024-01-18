package warlockfe.warlock3.stormfront.protocol.elements

import warlockfe.warlock3.core.compass.DirectionType
import warlockfe.warlock3.stormfront.protocol.BaseElementListener
import warlockfe.warlock3.stormfront.protocol.StartElement
import warlockfe.warlock3.stormfront.protocol.StormfrontDirectionEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontEvent

class DirHandler : BaseElementListener() {
    override fun startElement(element: StartElement): StormfrontEvent? {
        return element.attributes["value"]?.let { abbr ->
            DirectionType.fromAbbreviation(abbr)?.let {
                StormfrontDirectionEvent(it)
            }
        }
    }
}