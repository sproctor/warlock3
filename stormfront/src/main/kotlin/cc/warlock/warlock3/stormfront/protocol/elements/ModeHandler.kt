package cc.warlock.warlock3.stormfront.protocol.elements

import cc.warlock.warlock3.stormfront.protocol.BaseElementListener
import cc.warlock.warlock3.stormfront.protocol.StartElement
import cc.warlock.warlock3.stormfront.protocol.StormfrontModeEvent

class ModeHandler : BaseElementListener() {
    override fun startElement(element: StartElement): StormfrontModeEvent {
        return StormfrontModeEvent(id = element.attributes["id"])
    }
}