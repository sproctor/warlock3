package warlockfe.warlock3.wrayth.protocol.elements

import warlockfe.warlock3.wrayth.protocol.BaseElementListener
import warlockfe.warlock3.wrayth.protocol.StartElement
import warlockfe.warlock3.wrayth.protocol.WraythCloseDialogEvent
import warlockfe.warlock3.wrayth.protocol.WraythEvent

class CloseDialogHandler : BaseElementListener() {
    override fun startElement(element: StartElement): WraythEvent? {
        val id = element.attributes["id"] ?: return null
        return WraythCloseDialogEvent(id = id)
    }
}
