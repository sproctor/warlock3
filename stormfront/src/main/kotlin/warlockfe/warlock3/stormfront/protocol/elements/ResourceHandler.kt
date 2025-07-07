package warlockfe.warlock3.stormfront.protocol.elements

import warlockfe.warlock3.stormfront.protocol.BaseElementListener
import warlockfe.warlock3.stormfront.protocol.StartElement
import warlockfe.warlock3.stormfront.protocol.StormfrontResourceEvent

class ResourceHandler : BaseElementListener() {
    override fun startElement(element: StartElement): StormfrontResourceEvent? {
        val picture = element.attributes["picture"]?.takeIf { it != "0" } ?: return null
        return StormfrontResourceEvent(picture)
    }
}