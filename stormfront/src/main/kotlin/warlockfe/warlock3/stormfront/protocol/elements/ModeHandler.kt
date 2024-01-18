package warlockfe.warlock3.stormfront.protocol.elements

import warlockfe.warlock3.stormfront.protocol.BaseElementListener
import warlockfe.warlock3.stormfront.protocol.StartElement
import warlockfe.warlock3.stormfront.protocol.StormfrontModeEvent

class ModeHandler : BaseElementListener() {
    override fun startElement(element: StartElement): StormfrontModeEvent {
        return StormfrontModeEvent(id = element.attributes["id"])
    }
}