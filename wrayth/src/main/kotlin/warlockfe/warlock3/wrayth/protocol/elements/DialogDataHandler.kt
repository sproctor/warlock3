package warlockfe.warlock3.wrayth.protocol.elements

import warlockfe.warlock3.wrayth.protocol.*
import warlockfe.warlock3.wrayth.protocol.WraythDialogDataEvent
import warlockfe.warlock3.wrayth.protocol.WraythEvent

class DialogDataHandler : BaseElementListener() {
    override fun startElement(element: StartElement): WraythEvent {
        return WraythDialogDataEvent(
            id = element.attributes["id"],
            clear = element.attributes["clear"]?.startsWith(prefix = "t", ignoreCase = true) == true,
        )
    }

    override fun endElement(): WraythEvent {
        return WraythDialogDataEvent(null, clear = false)
    }
}