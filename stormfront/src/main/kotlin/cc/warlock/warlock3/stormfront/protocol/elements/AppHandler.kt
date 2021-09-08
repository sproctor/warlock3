package cc.warlock.warlock3.stormfront.protocol.elements

import cc.warlock.warlock3.stormfront.protocol.BaseElementListener
import cc.warlock.warlock3.stormfront.protocol.StartElement
import cc.warlock.warlock3.stormfront.protocol.StormfrontAppEvent
import cc.warlock.warlock3.stormfront.protocol.StormfrontEvent

class AppHandler : BaseElementListener() {
    override fun startElement(element: StartElement): StormfrontEvent {
        return StormfrontAppEvent(
            character = element.attributes["char"],
            game = element.attributes["game"],
            // title = element.attributes["title"]
        )
    }
}