package cc.warlock.warlock3.stormfront.protocol.elements

import cc.warlock.warlock3.core.WarlockStyle
import cc.warlock.warlock3.stormfront.StyleProvider
import cc.warlock.warlock3.stormfront.protocol.*

class PushBoldHandler : BaseElementListener() {
    override fun startElement(element: StartElement): StormfrontEvent {
        return StormfrontStyleEvent(StyleProvider.boldStyle)
    }
}