package warlockfe.warlock3.wrayth.protocol.elements

import warlockfe.warlock3.wrayth.protocol.BaseElementListener
import warlockfe.warlock3.wrayth.protocol.StartElement
import warlockfe.warlock3.wrayth.protocol.WraythDialogDataEvent
import warlockfe.warlock3.wrayth.protocol.WraythEvent

class DialogDataHandler : BaseElementListener() {
    override fun startElement(element: StartElement): WraythEvent =
        WraythDialogDataEvent(
            id = element.attributes["id"],
            clear = element.attributes["clear"]?.startsWith(prefix = "t", ignoreCase = true) == true,
        )

    override fun endElement(): WraythEvent = WraythDialogDataEvent(null, clear = false)
}
