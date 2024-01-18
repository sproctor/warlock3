package warlockfe.warlock3.stormfront.protocol.elements

import warlockfe.warlock3.stormfront.protocol.BaseElementListener
import warlockfe.warlock3.stormfront.protocol.StartElement
import warlockfe.warlock3.stormfront.protocol.StormfrontAppEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontEvent

class AppHandler : BaseElementListener() {
    override fun startElement(element: StartElement): StormfrontEvent {
        return StormfrontAppEvent(
            character = element.attributes["char"],
            game = element.attributes["game"],
            // title = element.attributes["title"]
        )
    }
}