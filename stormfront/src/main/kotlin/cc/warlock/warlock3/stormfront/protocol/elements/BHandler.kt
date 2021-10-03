package cc.warlock.warlock3.stormfront.protocol.elements

import cc.warlock.warlock3.stormfront.StyleProvider
import cc.warlock.warlock3.stormfront.protocol.*

class BHandler : BaseElementListener() {
    override fun startElement(element: StartElement): StormfrontEvent {
        return StormfrontStyleEvent(StyleProvider.boldStyle)
    }

    override fun endElement(element: EndElement): StormfrontEvent {
        return StormfrontStyleEvent(null)
    }
}