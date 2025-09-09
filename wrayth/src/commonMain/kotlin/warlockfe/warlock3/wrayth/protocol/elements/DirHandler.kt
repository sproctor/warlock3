package warlockfe.warlock3.wrayth.protocol.elements

import warlockfe.warlock3.core.compass.DirectionType
import warlockfe.warlock3.wrayth.protocol.BaseElementListener
import warlockfe.warlock3.wrayth.protocol.StartElement
import warlockfe.warlock3.wrayth.protocol.WraythDirectionEvent
import warlockfe.warlock3.wrayth.protocol.WraythEvent

class DirHandler : BaseElementListener() {
    override fun startElement(element: StartElement): WraythEvent? {
        return element.attributes["value"]?.let { abbr ->
            DirectionType.fromAbbreviation(abbr)?.let {
                WraythDirectionEvent(it)
            }
        }
    }
}