package warlockfe.warlock3.stormfront.protocol.elements

import warlockfe.warlock3.core.text.WarlockStyle
import warlockfe.warlock3.stormfront.protocol.BaseElementListener
import warlockfe.warlock3.stormfront.protocol.StartElement
import warlockfe.warlock3.stormfront.protocol.StormfrontEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontPopStyleEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontPushStyleEvent

class PresetHandler : BaseElementListener() {
    override fun startElement(element: StartElement): StormfrontEvent {
        val style = element.attributes["id"]?.let { WarlockStyle(it) }
        return StormfrontPushStyleEvent(style ?: WarlockStyle.Default)
    }

    override fun endElement(): StormfrontEvent {
        return StormfrontPopStyleEvent
    }
}