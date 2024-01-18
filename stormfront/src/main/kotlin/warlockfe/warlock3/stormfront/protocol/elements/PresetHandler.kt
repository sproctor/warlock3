package warlockfe.warlock3.stormfront.protocol.elements

import warlockfe.warlock3.core.text.WarlockStyle
import warlockfe.warlock3.stormfront.protocol.*
import warlockfe.warlock3.stormfront.protocol.StormfrontEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontStyleEvent

class PresetHandler : BaseElementListener() {
    override fun startElement(element: StartElement): StormfrontStyleEvent {
        val style = element.attributes["id"]?.let { WarlockStyle(it) }
        return StormfrontStyleEvent(style)
    }

    override fun endElement(): StormfrontEvent {
        return StormfrontStyleEvent(null)
    }
}