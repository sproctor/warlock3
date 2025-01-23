package warlockfe.warlock3.stormfront.protocol.elements

import warlockfe.warlock3.core.text.WarlockStyle
import warlockfe.warlock3.stormfront.protocol.BaseElementListener
import warlockfe.warlock3.stormfront.protocol.StartElement
import warlockfe.warlock3.stormfront.protocol.StormfrontPopStyleEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontPushStyleEvent

class StyleHandler : BaseElementListener() {
    override fun startElement(element: StartElement): StormfrontPushStyleEvent {
        return element.attributes["id"]
            ?.let { WarlockStyle(it) }
            .let { StormfrontPushStyleEvent(it ?: WarlockStyle.Default) }
    }

    override fun endElement(): StormfrontPopStyleEvent {
        return StormfrontPopStyleEvent
    }
}