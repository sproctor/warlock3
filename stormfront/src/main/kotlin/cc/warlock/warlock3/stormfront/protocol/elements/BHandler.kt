package cc.warlock.warlock3.stormfront.protocol.elements

import cc.warlock.warlock3.core.text.WarlockStyle
import cc.warlock.warlock3.stormfront.protocol.*

class BHandler : BaseElementListener() {
    override fun startElement(element: StartElement): StormfrontEvent {
        return StormfrontStyleEvent(WarlockStyle.Bold)
    }

    override fun endElement(element: EndElement): StormfrontEvent {
        return StormfrontStyleEvent(null)
    }
}