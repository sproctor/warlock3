package warlockfe.warlock3.wrayth.protocol.elements

import warlockfe.warlock3.wrayth.protocol.BaseElementListener
import warlockfe.warlock3.wrayth.protocol.StartElement
import warlockfe.warlock3.wrayth.protocol.WraythEvent
import warlockfe.warlock3.wrayth.protocol.WraythMenuItemEvent

class MiHandler : BaseElementListener() {
    override fun startElement(element: StartElement): WraythEvent? {
        val coord = element.attributes["coord"] ?: return null
        return WraythMenuItemEvent(
            coord = coord,
            noun = element.attributes["noun"],
            category = element.attributes["menu_cat"],
        )
    }
}
