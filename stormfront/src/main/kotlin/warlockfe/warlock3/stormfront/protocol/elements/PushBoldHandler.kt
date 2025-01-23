package warlockfe.warlock3.stormfront.protocol.elements

import warlockfe.warlock3.core.text.WarlockStyle
import warlockfe.warlock3.stormfront.protocol.BaseElementListener
import warlockfe.warlock3.stormfront.protocol.StartElement
import warlockfe.warlock3.stormfront.protocol.StormfrontEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontPushStyleEvent

class PushBoldHandler() : BaseElementListener() {
    override fun startElement(element: StartElement): StormfrontEvent {
        return StormfrontPushStyleEvent(WarlockStyle.Bold)
    }
}