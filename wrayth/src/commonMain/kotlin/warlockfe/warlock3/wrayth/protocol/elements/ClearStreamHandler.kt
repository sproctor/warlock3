package warlockfe.warlock3.wrayth.protocol.elements

import warlockfe.warlock3.wrayth.protocol.*
import warlockfe.warlock3.wrayth.protocol.WraythClearStreamEvent
import warlockfe.warlock3.wrayth.protocol.WraythEvent
import warlockfe.warlock3.wrayth.protocol.WraythHandledEvent

class ClearStreamHandler : BaseElementListener() {
    override fun startElement(element: StartElement): WraythEvent {
        return element.attributes["id"]?.let { id ->
            WraythClearStreamEvent(id)
        } ?: WraythHandledEvent
    }
}