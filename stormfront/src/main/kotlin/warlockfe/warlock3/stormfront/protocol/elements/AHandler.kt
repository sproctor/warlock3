package warlockfe.warlock3.stormfront.protocol.elements

import warlockfe.warlock3.core.text.WarlockStyle
import warlockfe.warlock3.stormfront.protocol.BaseElementListener
import warlockfe.warlock3.stormfront.protocol.StartElement
import warlockfe.warlock3.stormfront.protocol.StormfrontEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontPopStyleEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontPushStyleEvent

class AHandler : BaseElementListener() {
    override fun startElement(element: StartElement): StormfrontEvent {
        val url = element.attributes["href"]
        val action = if (url != null) {
            Pair("url", url)
        } else {
            // TODO handle GS stuff here
            null
        }
        return StormfrontPushStyleEvent(WarlockStyle.Link(action))
    }

    override fun endElement(): StormfrontEvent {
        return StormfrontPopStyleEvent
    }
}