package cc.warlock.warlock3.stormfront.protocol.elements

import cc.warlock.warlock3.core.WarlockStyle
import cc.warlock.warlock3.stormfront.protocol.BaseElementListener
import cc.warlock.warlock3.stormfront.protocol.StartElement
import cc.warlock.warlock3.stormfront.protocol.StormfrontPushStyleEvent

class StyleHandler : BaseElementListener() {
    override fun startElement(element: StartElement): StormfrontPushStyleEvent? {
        return element.attributes["id"]?.let {
            StormfrontPushStyleEvent(WarlockStyle(name = it))
        }
    }
}