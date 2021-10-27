package cc.warlock.warlock3.stormfront.protocol.elements

import cc.warlock.warlock3.core.text.StyleProvider
import cc.warlock.warlock3.stormfront.protocol.*

class PresetHandler : BaseElementListener() {
    override fun startElement(element: StartElement): StormfrontStyleEvent {
        val style = element.attributes["id"]?.let { StyleProvider.getStyle(it) }
        return StormfrontStyleEvent(style)
    }

    override fun endElement(element: EndElement): StormfrontEvent {
        return StormfrontStyleEvent(null)
    }
}