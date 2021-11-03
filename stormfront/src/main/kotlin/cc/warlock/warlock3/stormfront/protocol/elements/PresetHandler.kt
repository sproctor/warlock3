package cc.warlock.warlock3.stormfront.protocol.elements

import cc.warlock.warlock3.core.text.StyleRepository
import cc.warlock.warlock3.core.text.WarlockStyle
import cc.warlock.warlock3.stormfront.protocol.*

class PresetHandler() : BaseElementListener() {
    override fun startElement(element: StartElement): StormfrontStyleEvent {
        val style = element.attributes["id"]?.let { WarlockStyle(it) }
        return StormfrontStyleEvent(style)
    }

    override fun endElement(element: EndElement): StormfrontEvent {
        return StormfrontStyleEvent(null)
    }
}