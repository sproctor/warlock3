package cc.warlock.warlock3.stormfront.protocol.elements

import cc.warlock.warlock3.core.text.WarlockStyle
import cc.warlock.warlock3.stormfront.protocol.BaseElementListener
import cc.warlock.warlock3.stormfront.protocol.StartElement
import cc.warlock.warlock3.stormfront.protocol.StormfrontEvent
import cc.warlock.warlock3.stormfront.protocol.StormfrontStyleEvent

class PushBoldHandler() : BaseElementListener() {
    override fun startElement(element: StartElement): StormfrontEvent {
        return StormfrontStyleEvent(WarlockStyle.Bold)
    }
}