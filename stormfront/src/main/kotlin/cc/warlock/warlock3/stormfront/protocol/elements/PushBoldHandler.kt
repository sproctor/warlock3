package cc.warlock.warlock3.stormfront.protocol.elements

import cc.warlock.warlock3.core.text.StyleProvider
import cc.warlock.warlock3.stormfront.protocol.*

class PushBoldHandler : BaseElementListener() {
    override fun startElement(element: StartElement): StormfrontEvent {
        return StormfrontStyleEvent(StyleProvider.boldStyle)
    }
}