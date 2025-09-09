package warlockfe.warlock3.wrayth.protocol.elements

import warlockfe.warlock3.wrayth.protocol.BaseElementListener
import warlockfe.warlock3.wrayth.protocol.StartElement
import warlockfe.warlock3.wrayth.protocol.WraythResourceEvent

class ResourceHandler : BaseElementListener() {
    override fun startElement(element: StartElement): WraythResourceEvent? {
        val picture = element.attributes["picture"]?.takeIf { it != "0" } ?: return null
        return WraythResourceEvent(picture)
    }
}