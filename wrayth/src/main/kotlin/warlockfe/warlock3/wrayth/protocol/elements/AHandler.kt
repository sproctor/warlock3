package warlockfe.warlock3.wrayth.protocol.elements

import warlockfe.warlock3.core.client.WarlockAction
import warlockfe.warlock3.core.text.WarlockStyle
import warlockfe.warlock3.wrayth.protocol.BaseElementListener
import warlockfe.warlock3.wrayth.protocol.StartElement
import warlockfe.warlock3.wrayth.protocol.WraythEvent
import warlockfe.warlock3.wrayth.protocol.WraythPopStyleEvent
import warlockfe.warlock3.wrayth.protocol.WraythPushCmdEvent
import warlockfe.warlock3.wrayth.protocol.WraythPushStyleEvent
import warlockfe.warlock3.wrayth.util.WraythCmd

class AHandler : BaseElementListener() {
    override fun startElement(element: StartElement): WraythEvent {
        val url = element.attributes["href"]
        return if (url != null) {
            WraythPushStyleEvent(WarlockStyle.Link(WarlockAction.OpenLink(url)))
        } else {
            WraythPushCmdEvent(
                WraythCmd(
                    coord = element.attributes["coord"],
                    noun = element.attributes["noun"],
                    exist = element.attributes["exist"],
                )
            )
        }

    }

    override fun endElement(): WraythEvent {
        return WraythPopStyleEvent
    }
}