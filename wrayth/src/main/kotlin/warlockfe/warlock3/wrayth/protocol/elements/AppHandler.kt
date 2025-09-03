package warlockfe.warlock3.wrayth.protocol.elements

import warlockfe.warlock3.wrayth.protocol.BaseElementListener
import warlockfe.warlock3.wrayth.protocol.StartElement
import warlockfe.warlock3.wrayth.protocol.WraythAppEvent
import warlockfe.warlock3.wrayth.protocol.WraythEvent

class AppHandler : BaseElementListener() {
    override fun startElement(element: StartElement): WraythEvent {
        return WraythAppEvent(
            character = element.attributes["char"],
            game = element.attributes["game"],
            // title = element.attributes["title"]
        )
    }
}