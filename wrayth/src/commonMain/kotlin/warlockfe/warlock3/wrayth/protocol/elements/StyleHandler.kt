package warlockfe.warlock3.wrayth.protocol.elements

import warlockfe.warlock3.core.text.WarlockStyle
import warlockfe.warlock3.wrayth.protocol.BaseElementListener
import warlockfe.warlock3.wrayth.protocol.StartElement
import warlockfe.warlock3.wrayth.protocol.WraythEvent
import warlockfe.warlock3.wrayth.protocol.WraythStyleEvent

class StyleHandler : BaseElementListener() {
    override fun startElement(element: StartElement): WraythEvent {
        return element.attributes["id"]
            ?.let { WarlockStyle(it) }
            .let { WraythStyleEvent(it) }
    }
}
