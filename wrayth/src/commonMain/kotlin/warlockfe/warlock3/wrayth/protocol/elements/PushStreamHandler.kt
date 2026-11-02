package warlockfe.warlock3.wrayth.protocol.elements

import warlockfe.warlock3.wrayth.protocol.BaseElementListener
import warlockfe.warlock3.wrayth.protocol.StartElement
import warlockfe.warlock3.wrayth.protocol.WraythEvent
import warlockfe.warlock3.wrayth.protocol.WraythStreamEvent

class PushStreamHandler : BaseElementListener() {
    override fun startElement(element: StartElement): WraythEvent? =
        element.attributes["id"]?.let { id ->
            // Wrayth refuses a push to a window nobody declared, and the text stays in main.
            WraythStreamEvent(id = id, needsWindow = true)
        }
}
