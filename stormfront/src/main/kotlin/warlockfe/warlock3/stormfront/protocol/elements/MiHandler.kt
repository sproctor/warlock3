package warlockfe.warlock3.stormfront.protocol.elements

import warlockfe.warlock3.stormfront.protocol.BaseElementListener
import warlockfe.warlock3.stormfront.protocol.StartElement
import warlockfe.warlock3.stormfront.protocol.StormfrontEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontMenuItemEvent

class MiHandler : BaseElementListener() {
    override fun startElement(element: StartElement): StormfrontEvent? {
        val coord = element.attributes["coord"] ?: return null
        return StormfrontMenuItemEvent(
            coord = coord,
            noun = element.attributes["noun"],
            category = element.attributes["menu_cat"],
        )
    }
}
