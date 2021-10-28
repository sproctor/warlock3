package cc.warlock.warlock3.stormfront.protocol.elements

import cc.warlock.warlock3.core.text.StyleRegistry
import cc.warlock.warlock3.stormfront.protocol.*

class BHandler(private val styleRegistry: StyleRegistry) : BaseElementListener() {
    override fun startElement(element: StartElement): StormfrontEvent {
        return StormfrontStyleEvent(styleRegistry.boldStyle)
    }

    override fun endElement(element: EndElement): StormfrontEvent {
        return StormfrontStyleEvent(null)
    }
}