package warlockfe.warlock3.wrayth.protocol.elements

import warlockfe.warlock3.wrayth.protocol.BaseElementListener
import warlockfe.warlock3.wrayth.protocol.StartElement
import warlockfe.warlock3.wrayth.protocol.WraythEvent
import warlockfe.warlock3.wrayth.protocol.WraythPanelDataEvent

class PanelDataHandler : BaseElementListener() {
    override fun startElement(element: StartElement): WraythEvent =
        WraythPanelDataEvent(
            id = element.attributes["id"],
            clear = element.attributes["clear"]?.startsWith(prefix = "t", ignoreCase = true) == true,
        )

    override fun endElement(): WraythEvent = WraythPanelDataEvent(null, clear = false)
}
