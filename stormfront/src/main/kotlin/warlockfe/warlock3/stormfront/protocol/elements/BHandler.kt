package warlockfe.warlock3.stormfront.protocol.elements

import warlockfe.warlock3.core.text.WarlockStyle
import warlockfe.warlock3.stormfront.protocol.*
import warlockfe.warlock3.stormfront.protocol.StormfrontEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontStyleEvent

class BHandler : BaseElementListener() {
    override fun startElement(element: StartElement): StormfrontEvent {
        return StormfrontStyleEvent(WarlockStyle.Bold)
    }

    override fun endElement(): StormfrontEvent {
        return StormfrontStyleEvent(null)
    }
}