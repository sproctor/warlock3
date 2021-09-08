package cc.warlock.warlock3.stormfront.protocol.elements

import cc.warlock.warlock3.stormfront.protocol.BaseElementListener
import cc.warlock.warlock3.stormfront.protocol.StartElement
import cc.warlock.warlock3.stormfront.protocol.StormfrontEvent
import cc.warlock.warlock3.stormfront.protocol.StormfrontPopStyleEvent

class PopBoldHandler  : BaseElementListener() {
    override fun startElement(element: StartElement): StormfrontEvent {
        return StormfrontPopStyleEvent
    }
}