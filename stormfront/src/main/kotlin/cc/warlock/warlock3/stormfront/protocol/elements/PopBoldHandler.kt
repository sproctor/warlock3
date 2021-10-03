package cc.warlock.warlock3.stormfront.protocol.elements

import cc.warlock.warlock3.stormfront.protocol.*

class PopBoldHandler  : BaseElementListener() {
    override fun startElement(element: StartElement): StormfrontEvent {
        return StormfrontStyleEvent(null)
    }
}