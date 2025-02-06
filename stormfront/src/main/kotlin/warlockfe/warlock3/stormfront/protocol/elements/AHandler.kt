package warlockfe.warlock3.stormfront.protocol.elements

import warlockfe.warlock3.core.text.WarlockStyle
import warlockfe.warlock3.stormfront.protocol.BaseElementListener
import warlockfe.warlock3.stormfront.protocol.StartElement
import warlockfe.warlock3.stormfront.protocol.StormfrontEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontPopStyleEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontPushCmdEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontPushStyleEvent
import warlockfe.warlock3.stormfront.util.StormfrontCmd

class AHandler : BaseElementListener() {
    override fun startElement(element: StartElement): StormfrontEvent {
        val url = element.attributes["href"]
        if (url != null) {
            return StormfrontPushStyleEvent(WarlockStyle.Link(Pair("url", url)))
        } else {
            return StormfrontPushCmdEvent(
                StormfrontCmd(
                    coord = element.attributes["coord"],
                    noun = element.attributes["noun"],
                    exist = element.attributes["exist"],
                )
            )
        }

    }

    override fun endElement(): StormfrontEvent {
        return StormfrontPopStyleEvent
    }
}