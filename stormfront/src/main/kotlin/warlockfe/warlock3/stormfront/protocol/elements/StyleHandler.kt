package warlockfe.warlock3.stormfront.protocol.elements

import warlockfe.warlock3.core.text.WarlockStyle
import warlockfe.warlock3.stormfront.protocol.BaseElementListener
import warlockfe.warlock3.stormfront.protocol.StartElement
import warlockfe.warlock3.stormfront.protocol.StormfrontStyleEvent

class StyleHandler : BaseElementListener() {
    override fun startElement(element: StartElement): StormfrontStyleEvent {
        val style = element.attributes["id"]?.let { WarlockStyle(it) }
        return StormfrontStyleEvent(style)
    }
}